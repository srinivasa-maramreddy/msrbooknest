package com.bookstore.auth.dto;

public class PasswordRecoveryResponse {
    private final String message;
    private final String maskedEmail;
    private final String debugOtp;

    public PasswordRecoveryResponse(String message, String maskedEmail, String debugOtp) {
        this.message = message;
        this.maskedEmail = maskedEmail;
        this.debugOtp = debugOtp;
    }

    public String getMessage() { return message; }
    public String getMaskedEmail() { return maskedEmail; }
    public String getDebugOtp() { return debugOtp; }
}
