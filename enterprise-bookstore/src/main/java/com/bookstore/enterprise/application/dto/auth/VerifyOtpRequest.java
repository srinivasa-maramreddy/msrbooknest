package com.bookstore.enterprise.application.dto.auth;

import com.bookstore.enterprise.domain.model.OtpPurpose;
import com.bookstore.enterprise.domain.model.VerificationChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class VerifyOtpRequest {

    @NotNull
    private VerificationChannel verificationChannel;

    @NotBlank
    private String destination;

    @NotBlank
    private String otpCode;

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

    public String getOtpCode() {
        return otpCode;
    }

    public void setOtpCode(String otpCode) {
        this.otpCode = otpCode;
    }

    public OtpPurpose getPurpose() {
        return purpose;
    }

    public void setPurpose(OtpPurpose purpose) {
        this.purpose = purpose;
    }
}
