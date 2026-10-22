package com.bookstore.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class ResendOtpRequest {
    @NotBlank
    private String verificationChannel;

    @NotBlank
    private String destination;

    public String getVerificationChannel() {
        return verificationChannel;
    }

    public void setVerificationChannel(String verificationChannel) {
        this.verificationChannel = verificationChannel;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }
}
