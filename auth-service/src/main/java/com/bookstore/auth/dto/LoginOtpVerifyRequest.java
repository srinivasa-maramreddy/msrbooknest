package com.bookstore.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class LoginOtpVerifyRequest {
    @NotBlank
    private String identifier;

    @NotBlank
    private String otpCode;

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getOtpCode() {
        return otpCode;
    }

    public void setOtpCode(String otpCode) {
        this.otpCode = otpCode;
    }
}
