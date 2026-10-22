package com.bookstore.auth.dto;

public class AuthResponse {
    private String token;
    private String email;
    private String loginId;
    private String role;

    public AuthResponse(String token, String loginId, String role) {
        this.token = token;
        this.email = loginId;
        this.loginId = loginId;
        this.role = role;
    }

    public String getToken() { return token; }
    public String getEmail() { return email; }
    public String getLoginId() { return loginId; }
    public String getRole() { return role; }
}
