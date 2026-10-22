package com.bookstore.auth.service;

import com.bookstore.auth.entity.OtpPurpose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailOtpService {
    private static final Logger log = LoggerFactory.getLogger(EmailOtpService.class);

    private final JavaMailSender mailSender;

    @Value("${app.email.otp.enabled:false}")
    private boolean enabled;

    @Value("${app.email.otp.from:}")
    private String fromEmail;

    @Value("${app.email.otp.subject-prefix:Bookstore}")
    private String subjectPrefix;

    public EmailOtpService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendOtp(String toEmail, String otpCode, OtpPurpose purpose) {
        if (!enabled) {
            throw new IllegalStateException("Email OTP delivery is not configured");
        }
        if (fromEmail == null || fromEmail.isBlank()) {
            throw new IllegalStateException("Email OTP sender is not configured");
        }

        String subject = subjectPrefix + " OTP - " + purpose.name();
        String body = "Your OTP is " + otpCode + ". It expires in 10 minutes.";
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Email OTP sent purpose={} email={}", purpose.name(), toEmail);
        } catch (Exception ex) {
            log.error("Email OTP delivery failed purpose={} email={} errorType={}",
                    purpose.name(), toEmail, ex.getClass().getSimpleName());
            throw new IllegalStateException("Could not deliver email verification code. Check the SMTP configuration and try again.");
        }
    }
}
