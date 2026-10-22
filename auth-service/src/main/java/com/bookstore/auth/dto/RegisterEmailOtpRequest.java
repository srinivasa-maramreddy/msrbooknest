package com.bookstore.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class RegisterEmailOtpRequest {
    @NotBlank
    private String registrationId;

    @NotBlank
    @Email
    private String email;

    public String getRegistrationId() {
        return registrationId;
    }

    public String getEmail() {
        return email;
    }

    public void setRegistrationId(String registrationId) {
        this.registrationId = registrationId;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
