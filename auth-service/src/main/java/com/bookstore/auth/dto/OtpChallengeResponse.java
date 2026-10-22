package com.bookstore.auth.dto;

public class OtpChallengeResponse {
    private String message;
    private String destination;
    private String verificationChannel;

    public OtpChallengeResponse(String message, String destination, String verificationChannel) {
        this.message = message;
        this.destination = destination;
        this.verificationChannel = verificationChannel;
    }

    public String getMessage() {
        return message;
    }

    public String getDestination() {
        return destination;
    }

    public String getVerificationChannel() {
        return verificationChannel;
    }
}
