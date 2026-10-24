package com.bookstore.enterprise.infrastructure.notification;

import com.bookstore.enterprise.application.notification.OtpNotifier;
import com.bookstore.enterprise.domain.model.VerificationChannel;
import com.bookstore.enterprise.infrastructure.config.OtpProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class LoggingOtpNotifier implements OtpNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingOtpNotifier.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final OtpProperties otpProperties;

    public LoggingOtpNotifier(ObjectProvider<JavaMailSender> mailSenderProvider, OtpProperties otpProperties) {
        this.mailSenderProvider = mailSenderProvider;
        this.otpProperties = otpProperties;
    }

    @Override
    public void sendOtp(VerificationChannel channel, String destination, String otpCode) {
        if (channel != VerificationChannel.EMAIL) {
            throw new IllegalArgumentException("Only email OTP delivery is supported");
        }
        sendEmailOtp(destination, otpCode);
    }

    private void sendEmailOtp(String destination, String otpCode) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (!otpProperties.isEmailEnabled() || mailSender == null) {
            log.info("OTP sent channel=EMAIL destination={} otp={}", destination, otpCode);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(otpProperties.getMailFrom());
            message.setTo(destination);
            message.setSubject("Your Bookstore OTP Code");
            message.setText("Your OTP code is " + otpCode + ". It expires in "
                    + otpProperties.getExpirationMinutes() + " minutes.");
            mailSender.send(message);
            log.info("OTP email delivered destination={}", destination);
        } catch (Exception ex) {
            log.error("Failed to send OTP email. Falling back to log destination={} reason={}", destination, ex.getMessage());
            log.info("OTP sent channel=EMAIL destination={} otp={}", destination, otpCode);
        }
    }
}
