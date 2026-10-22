package com.bookstore.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class RegisterCompleteRequest {
    @NotBlank
    private String registrationId;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String emailOtpCode;

    @NotBlank
    private String password;

    @NotBlank
    private String confirmPassword;

    public String getRegistrationId() {
        return registrationId;
    }

    public String getEmail() {
        return email;
    }

    public String getEmailOtpCode() {
        return emailOtpCode;
    }

    public String getPassword() {
        return password;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setRegistrationId(String registrationId) {
        this.registrationId = registrationId;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setEmailOtpCode(String emailOtpCode) {
        this.emailOtpCode = emailOtpCode;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
}
