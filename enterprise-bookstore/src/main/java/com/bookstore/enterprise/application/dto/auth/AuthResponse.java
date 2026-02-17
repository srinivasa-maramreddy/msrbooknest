package com.bookstore.enterprise.application.dto.auth;

import com.bookstore.enterprise.domain.model.Role;

public class AuthResponse {

    private String token;
    private String fullName;
    private String email;
    private Role role;

    public AuthResponse() {
    }

    public AuthResponse(String token, String fullName, String email, Role role) {
        this.token = token;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }
}
