package com.bookstore.enterprise.infrastructure.security;

import com.bookstore.enterprise.application.security.OtpCodeGenerator;
import com.bookstore.enterprise.infrastructure.config.OtpProperties;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class NumericOtpCodeGenerator implements OtpCodeGenerator {

    private final SecureRandom secureRandom = new SecureRandom();
    private final OtpProperties otpProperties;

    public NumericOtpCodeGenerator(OtpProperties otpProperties) {
        this.otpProperties = otpProperties;
    }

    @Override
    public String generateCode() {
        int length = Math.max(4, otpProperties.getLength());
        int bound = (int) Math.pow(10, length);
        int value = secureRandom.nextInt(bound);
        return String.format("%0" + length + "d", value);
    }
}
