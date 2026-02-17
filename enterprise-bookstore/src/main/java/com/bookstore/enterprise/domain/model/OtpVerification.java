package com.bookstore.enterprise.domain.model;

import java.time.LocalDateTime;

public class OtpVerification {

    private Long id;
    private Long userId;
    private OtpPurpose purpose;
    private VerificationChannel channel;
    private String destination;
    private String otpHash;
    private LocalDateTime expiresAt;
    private boolean verified;
    private int attempts;
    private LocalDateTime createdAt;

    public OtpVerification() {
    }

    public OtpVerification(
            Long id,
            Long userId,
            OtpPurpose purpose,
            VerificationChannel channel,
            String destination,
            String otpHash,
            LocalDateTime expiresAt,
            boolean verified,
            int attempts,
            LocalDateTime createdAt
    ) {
        this.id = id;
        this.userId = userId;
        this.purpose = purpose;
        this.channel = channel;
        this.destination = destination;
        this.otpHash = otpHash;
        this.expiresAt = expiresAt;
        this.verified = verified;
        this.attempts = attempts;
        this.createdAt = createdAt;
    }

    public boolean isExpired(LocalDateTime now) {
        return expiresAt == null || now.isAfter(expiresAt);
    }

    public void incrementAttempts() {
        attempts += 1;
    }

    public void markVerified() {
        verified = true;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public VerificationChannel getChannel() {
        return channel;
    }

    public void setChannel(VerificationChannel channel) {
        this.channel = channel;
    }

    public OtpPurpose getPurpose() {
        return purpose;
    }

    public void setPurpose(OtpPurpose purpose) {
        this.purpose = purpose;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public String getOtpHash() {
        return otpHash;
    }

    public void setOtpHash(String otpHash) {
        this.otpHash = otpHash;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
