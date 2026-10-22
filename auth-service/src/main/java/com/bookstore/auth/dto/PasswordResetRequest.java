package com.bookstore.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class PasswordResetRequest {
    @NotBlank
    private String identifier;

    @NotBlank
    @Pattern(regexp = "\\d{6}", message = "Enter the 6-digit verification code")
    private String otpCode;

    @NotBlank
    private String newPassword;

    @NotBlank
    private String confirmPassword;

    public String getIdentifier() { return identifier; }
    public String getOtpCode() { return otpCode; }
    public String getNewPassword() { return newPassword; }
    public String getConfirmPassword() { return confirmPassword; }
    public void setIdentifier(String identifier) { this.identifier = identifier; }
    public void setOtpCode(String otpCode) { this.otpCode = otpCode; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
}
