package com.bookstore.auth.dto;

public class RegistrationStepResponse {
    private String message;
    private String registrationId;
    private String maskedPhone;
    private String maskedEmail;

    public RegistrationStepResponse(
            String message,
            String registrationId,
            String maskedPhone,
            String maskedEmail
    ) {
        this.message = message;
        this.registrationId = registrationId;
        this.maskedPhone = maskedPhone;
        this.maskedEmail = maskedEmail;
    }

    public String getMessage() {
        return message;
    }

    public String getRegistrationId() {
        return registrationId;
    }

    public String getMaskedPhone() {
        return maskedPhone;
    }

    public String getMaskedEmail() {
        return maskedEmail;
    }
}
