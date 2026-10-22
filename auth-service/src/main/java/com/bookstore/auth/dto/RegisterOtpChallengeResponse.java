package com.bookstore.auth.dto;

public class RegisterOtpChallengeResponse {
    private String message;
    private String maskedEmail;
    private String maskedPhone;

    public RegisterOtpChallengeResponse(String message, String maskedEmail, String maskedPhone) {
        this.message = message;
        this.maskedEmail = maskedEmail;
        this.maskedPhone = maskedPhone;
    }

    public String getMessage() {
        return message;
    }

    public String getMaskedEmail() {
        return maskedEmail;
    }

    public String getMaskedPhone() {
        return maskedPhone;
    }
}
