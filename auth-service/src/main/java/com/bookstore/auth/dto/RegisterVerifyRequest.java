package com.bookstore.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class RegisterVerifyRequest {
    @NotBlank
    private String email;

    @NotBlank
    private String phoneNumber;

    @NotBlank
    private String emailOtpCode;

    @NotBlank
    private String phoneOtpCode;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getEmailOtpCode() {
        return emailOtpCode;
    }

    public void setEmailOtpCode(String emailOtpCode) {
        this.emailOtpCode = emailOtpCode;
    }

    public String getPhoneOtpCode() {
        return phoneOtpCode;
    }

    public void setPhoneOtpCode(String phoneOtpCode) {
        this.phoneOtpCode = phoneOtpCode;
    }
}
