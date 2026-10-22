package com.bookstore.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "registration_sessions")
public class RegistrationSession {
    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 20)
    private String phoneNumber;

    @Column(length = 255)
    private String phoneOtpHash;

    private LocalDateTime phoneOtpExpiresAt;

    @Column(nullable = false)
    private int phoneOtpAttempts;

    @Column(nullable = false)
    private boolean phoneVerified;

    @Column(length = 255)
    private String email;

    @Column(length = 255)
    private String emailOtpHash;

    private LocalDateTime emailOtpExpiresAt;

    @Column(nullable = false)
    private int emailOtpAttempts;

    @Column(nullable = false)
    private boolean emailVerified;

    @Column(nullable = false)
    private boolean completed;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public String getPhoneOtpHash() { return phoneOtpHash; }
    public void setPhoneOtpHash(String phoneOtpHash) { this.phoneOtpHash = phoneOtpHash; }
    public LocalDateTime getPhoneOtpExpiresAt() { return phoneOtpExpiresAt; }
    public void setPhoneOtpExpiresAt(LocalDateTime phoneOtpExpiresAt) { this.phoneOtpExpiresAt = phoneOtpExpiresAt; }
    public int getPhoneOtpAttempts() { return phoneOtpAttempts; }
    public void setPhoneOtpAttempts(int phoneOtpAttempts) { this.phoneOtpAttempts = phoneOtpAttempts; }
    public boolean isPhoneVerified() { return phoneVerified; }
    public void setPhoneVerified(boolean phoneVerified) { this.phoneVerified = phoneVerified; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getEmailOtpHash() { return emailOtpHash; }
    public void setEmailOtpHash(String emailOtpHash) { this.emailOtpHash = emailOtpHash; }
    public LocalDateTime getEmailOtpExpiresAt() { return emailOtpExpiresAt; }
    public void setEmailOtpExpiresAt(LocalDateTime emailOtpExpiresAt) { this.emailOtpExpiresAt = emailOtpExpiresAt; }
    public int getEmailOtpAttempts() { return emailOtpAttempts; }
    public void setEmailOtpAttempts(int emailOtpAttempts) { this.emailOtpAttempts = emailOtpAttempts; }
    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
