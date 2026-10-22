package com.bookstore.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class RegisterPhoneVerifyRequest {
    @NotBlank
    private String registrationId;

    @NotBlank
    private String otpCode;

    public String getRegistrationId() {
        return registrationId;
    }

    public String getOtpCode() {
        return otpCode;
    }

    public void setRegistrationId(String registrationId) {
        this.registrationId = registrationId;
    }

    public void setOtpCode(String otpCode) {
        this.otpCode = otpCode;
    }
}
