package com.bookstore.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class VerifyOtpRequest {
    @NotBlank
    private String verificationChannel;

    @NotBlank
    private String destination;

    @NotBlank
    private String otpCode;

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

    public String getOtpCode() {
        return otpCode;
    }

    public void setOtpCode(String otpCode) {
        this.otpCode = otpCode;
    }
}
