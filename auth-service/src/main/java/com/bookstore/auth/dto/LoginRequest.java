package com.bookstore.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class LoginRequest {
    private String identifier;

    private String email;

    @NotBlank
    private String password;

    public String getIdentifier() { return identifier; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }

    public void setIdentifier(String identifier) { this.identifier = identifier; }
    public void setEmail(String email) { this.email = email; }
    public void setPassword(String password) { this.password = password; }
}
