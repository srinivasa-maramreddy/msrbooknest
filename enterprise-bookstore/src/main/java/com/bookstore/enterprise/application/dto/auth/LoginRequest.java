package com.bookstore.enterprise.application.dto.auth;

import com.bookstore.enterprise.domain.model.VerificationChannel;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class LoginRequest {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String password;

    private VerificationChannel verificationChannel;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public VerificationChannel getVerificationChannel() {
        return verificationChannel;
    }

    public void setVerificationChannel(VerificationChannel verificationChannel) {
        this.verificationChannel = verificationChannel;
    }
}
