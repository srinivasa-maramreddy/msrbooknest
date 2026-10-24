package com.bookstore.auth.service;

import com.bookstore.auth.entity.OtpPurpose;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Properties;

@Service
public class EmailOtpService {
    private static final Logger log = LoggerFactory.getLogger(EmailOtpService.class);
    private static final URI TOKEN_ENDPOINT = URI.create("https://oauth2.googleapis.com/token");
    private static final URI GMAIL_SEND_ENDPOINT = URI.create("https://gmail.googleapis.com/gmail/v1/users/me/messages/send");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper objectMapper;

    @Value("${app.email.otp.enabled:false}")
    private boolean enabled;

    @Value("${app.email.otp.sender-email:}")
    private String senderEmail;

    @Value("${app.email.otp.subject-prefix:MSRBookNest}")
    private String subjectPrefix;

    @Value("${app.email.otp.google.client-id:}")
    private String clientId;

    @Value("${app.email.otp.google.client-secret:}")
    private String clientSecret;

    @Value("${app.email.otp.google.refresh-token:}")
    private String refreshToken;

    private volatile CachedAccessToken cachedAccessToken;

    public EmailOtpService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void sendOtp(String toEmail, String otpCode, OtpPurpose purpose) {
        validateConfiguration();

        String action = switch (purpose) {
            case REGISTRATION -> "verify your email address";
            case PASSWORD_RESET -> "reset your password";
            case LOGIN -> "sign in to your account";
        };
        String subject = safeSubjectPrefix() + " - " + action;
        String body = "Hello,\n\n"
                + "Use this one-time code to " + action + " for MSRBookNest:\n\n"
                + otpCode + "\n\n"
                + "This code expires in 10 minutes and can be used only once.\n"
                + "If you did not request this code, you can ignore this email. Never share your code with anyone.\n\n"
                + "Thank you,\nMSRBookNest";

        try {
            String rawMessage = encodeMessage(toEmail, subject, body);
            String requestBody = objectMapper.writeValueAsString(Map.of("raw", rawMessage));
            HttpRequest request = HttpRequest.newBuilder(GMAIL_SEND_ENDPOINT)
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + getAccessToken())
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("Gmail API rejected OTP message purpose={} status={}", purpose.name(), response.statusCode());
                throw new IllegalStateException("Gmail did not accept the verification email. Check the Google API settings.");
            }
            log.info("Email OTP sent purpose={} destination={}", purpose.name(), maskEmail(toEmail));
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Gmail API OTP delivery interrupted purpose={}", purpose.name());
            throw new IllegalStateException("Email delivery was interrupted. Try again.");
        } catch (Exception ex) {
            log.error("Gmail API OTP delivery failed purpose={} errorType={}",
                    purpose.name(), ex.getClass().getSimpleName());
            throw new IllegalStateException("Could not send the email code. Check the Google API settings and try again.");
        }
    }

    private synchronized String getAccessToken() throws Exception {
        CachedAccessToken current = cachedAccessToken;
        if (current != null && current.expiresAt().isAfter(Instant.now().plusSeconds(30))) {
            return current.value();
        }

        String form = "client_id=" + formValue(clientId)
                + "&client_secret=" + formValue(clientSecret)
                + "&refresh_token=" + formValue(refreshToken)
                + "&grant_type=refresh_token";
        HttpRequest request = HttpRequest.newBuilder(TOKEN_ENDPOINT)
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.error("Google OAuth token refresh failed status={}", response.statusCode());
            throw new IllegalStateException("Gmail authorization failed. Check the Google OAuth settings.");
        }

        JsonNode payload = objectMapper.readTree(response.body());
        String token = payload.path("access_token").asText("");
        long expiresIn = payload.path("expires_in").asLong(3600);
        if (token.isBlank()) {
            throw new IllegalStateException("Google did not return an email access token.");
        }

        cachedAccessToken = new CachedAccessToken(token, Instant.now().plusSeconds(Math.max(60, expiresIn - 60)));
        return token;
    }

    private String encodeMessage(String toEmail, String subject, String body) throws Exception {
        InternetAddress from = new InternetAddress(senderEmail);
        from.validate();
        InternetAddress to = new InternetAddress(toEmail);
        to.validate();

        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
        helper.setFrom(from);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(body, false);
        message.saveChanges();

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        message.writeTo(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
    }

    private void validateConfiguration() {
        if (!enabled) {
            throw new IllegalStateException("Real email verification is not enabled on the Auth service.");
        }
        if (isBlank(senderEmail) || isBlank(clientId) || isBlank(clientSecret) || isBlank(refreshToken)) {
            throw new IllegalStateException("Real email verification is enabled but Google OAuth credentials are missing.");
        }
    }

    private String safeSubjectPrefix() {
        return subjectPrefix == null ? "MSRBookNest" : subjectPrefix.replaceAll("[\\r\\n]", " ").trim();
    }

    private String formValue(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String maskEmail(String email) {
        if (email == null) return "unknown";
        int at = email.indexOf('@');
        if (at < 1) return "***";
        return email.charAt(0) + "***" + email.substring(at);
    }

    private record CachedAccessToken(String value, Instant expiresAt) {
    }
}
