package com.bookstore.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "Name is required") @Size(max = 100, message = "Name must be at most 100 characters") String name,
        @Size(max = 24, message = "Enter a valid Indian mobile number") String phoneNumber,
        @Size(max = 1000, message = "Address must be at most 1000 characters") String address) {
}
