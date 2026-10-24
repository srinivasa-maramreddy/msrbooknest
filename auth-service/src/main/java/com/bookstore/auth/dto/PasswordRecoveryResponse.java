package com.bookstore.auth.dto;

public class PasswordRecoveryResponse {
    private final String message;
    private final String maskedEmail;

    public PasswordRecoveryResponse(String message, String maskedEmail) {
        this.message = message;
        this.maskedEmail = maskedEmail;
    }

    public String getMessage() { return message; }
    public String getMaskedEmail() { return maskedEmail; }
}
