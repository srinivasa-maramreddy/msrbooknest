package com.bookstore.enterprise.application.dto.auth;

import com.bookstore.enterprise.domain.model.OtpPurpose;
import com.bookstore.enterprise.domain.model.VerificationChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ResendOtpRequest {

    @NotNull
    private VerificationChannel verificationChannel;

    @NotBlank
    private String destination;

    private OtpPurpose purpose;

    public VerificationChannel getVerificationChannel() {
        return verificationChannel;
    }

    public void setVerificationChannel(VerificationChannel verificationChannel) {
        this.verificationChannel = verificationChannel;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public OtpPurpose getPurpose() {
        return purpose;
    }

    public void setPurpose(OtpPurpose purpose) {
        this.purpose = purpose;
    }
}
