package com.bookstore.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class Msg91OtpService {
    private static final Logger log = LoggerFactory.getLogger(Msg91OtpService.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${app.sms.msg91.enabled:false}")
    private boolean enabled;

    @Value("${app.sms.msg91.endpoint:https://control.msg91.com/api/v5/flow/}")
    private String endpoint;

    @Value("${app.sms.msg91.auth-key:}")
    private String authKey;

    @Value("${app.sms.msg91.template-id:}")
    private String templateId;

    @Value("${app.sms.msg91.country-code:91}")
    private String countryCode;

    @Value("${app.sms.msg91.otp-variable:OTP}")
    private String otpVariable;

    public Msg91OtpService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void sendOtp(String indianMobileNumber, String otpCode) {
        if (!enabled) {
            throw new IllegalStateException("SMS OTP delivery is not configured");
        }
        if (isBlank(authKey) || isBlank(templateId)) {
            throw new IllegalStateException("SMS OTP provider credentials are not configured");
        }

        String mobileWithCountryCode = countryCode + indianMobileNumber;
        try {
            JsonNode payload = buildPayload(mobileWithCountryCode, otpCode);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(15))
                    .header("accept", "application/json")
                    .header("content-type", "application/json")
                    .header("authkey", authKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String message = "MSG91 send failed status=" + response.statusCode();
                handleFailure(indianMobileNumber, message);
                return;
            }

            JsonNode body = objectMapper.readTree(response.body());
            if ("error".equalsIgnoreCase(body.path("type").asText())) {
                handleFailure(indianMobileNumber, "MSG91 rejected the OTP delivery request");
                return;
            }

            log.info("OTP SMS accepted by MSG91 for mobile ending {}", indianMobileNumber.substring(6));
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("MSG91 error type={} mobile={}", ex.getClass().getSimpleName(), indianMobileNumber);
            throw new IllegalStateException("Could not deliver SMS verification code. Check the MSG91 configuration and try again.");
        }
    }

    private JsonNode buildPayload(String mobileWithCountryCode, String otpCode) {
        var root = objectMapper.createObjectNode();
        root.put("template_id", templateId);
        root.put("short_url", "0");
        var recipients = root.putArray("recipients");
        var recipient = recipients.addObject();
        recipient.put("mobiles", mobileWithCountryCode);
        recipient.put(otpVariable, otpCode);
        return root;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void handleFailure(String mobile, String message) {
        log.error("{} mobile={}", message, mobile);
        throw new IllegalStateException("Could not deliver SMS verification code. Check the MSG91 configuration and try again.");
    }
}
