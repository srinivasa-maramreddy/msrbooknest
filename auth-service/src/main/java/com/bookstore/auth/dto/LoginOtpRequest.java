package com.bookstore.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class LoginOtpRequest {
    @NotBlank
    private String identifier;

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }
}
