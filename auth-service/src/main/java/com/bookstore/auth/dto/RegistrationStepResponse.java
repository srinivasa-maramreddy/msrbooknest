package com.bookstore.auth.dto;

public class RegistrationStepResponse {
    private String message;
    private String registrationId;
    private String maskedPhone;
    private String maskedEmail;
    private boolean phoneVerified;
    private String debugPhoneOtp;
    private String debugEmailOtp;

    public RegistrationStepResponse(
            String message,
            String registrationId,
            String maskedPhone,
            String maskedEmail,
            boolean phoneVerified,
            String debugPhoneOtp,
            String debugEmailOtp
    ) {
        this.message = message;
        this.registrationId = registrationId;
        this.maskedPhone = maskedPhone;
        this.maskedEmail = maskedEmail;
        this.phoneVerified = phoneVerified;
        this.debugPhoneOtp = debugPhoneOtp;
        this.debugEmailOtp = debugEmailOtp;
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

    public boolean isPhoneVerified() {
        return phoneVerified;
    }

    public String getDebugPhoneOtp() {
        return debugPhoneOtp;
    }

    public String getDebugEmailOtp() {
        return debugEmailOtp;
    }
}
