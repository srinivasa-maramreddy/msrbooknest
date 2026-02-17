package com.bookstore.enterprise.application.dto.auth;

import com.bookstore.enterprise.domain.model.OtpPurpose;
import com.bookstore.enterprise.domain.model.VerificationChannel;

public class RegisterInitiationResponse {

    private String message;
    private String destination;
    private VerificationChannel verificationChannel;
    private OtpPurpose purpose;

    public RegisterInitiationResponse() {
    }

    public RegisterInitiationResponse(
            String message,
            String destination,
            VerificationChannel verificationChannel,
            OtpPurpose purpose
    ) {
        this.message = message;
        this.destination = destination;
        this.verificationChannel = verificationChannel;
        this.purpose = purpose;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public VerificationChannel getVerificationChannel() {
        return verificationChannel;
    }

    public void setVerificationChannel(VerificationChannel verificationChannel) {
        this.verificationChannel = verificationChannel;
    }

    public OtpPurpose getPurpose() {
        return purpose;
    }

    public void setPurpose(OtpPurpose purpose) {
        this.purpose = purpose;
    }
}
