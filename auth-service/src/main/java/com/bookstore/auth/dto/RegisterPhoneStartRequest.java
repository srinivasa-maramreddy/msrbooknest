package com.bookstore.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class RegisterPhoneStartRequest {
    @NotBlank
    private String name;

    @NotBlank
    private String phoneNumber;

    public String getName() {
        return name;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
}
