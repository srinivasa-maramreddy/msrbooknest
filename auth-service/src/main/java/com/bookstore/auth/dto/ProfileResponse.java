package com.bookstore.auth.dto;

public record ProfileResponse(Long id, String name, String email, String phoneNumber, String address, String role) {
}
