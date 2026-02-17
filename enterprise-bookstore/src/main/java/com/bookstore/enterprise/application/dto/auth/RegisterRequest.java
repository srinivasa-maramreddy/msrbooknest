package com.bookstore.enterprise.application.dto.auth;

import com.bookstore.enterprise.domain.model.Role;
import com.bookstore.enterprise.domain.model.VerificationChannel;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegisterRequest {

    @NotBlank
    @Size(max = 120)
    private String fullName;

    @NotBlank
    @Email
    @Size(max = 180)
    private String email;

    @Pattern(regexp = "^[0-9]{10,15}$", message = "Phone number must be 10 to 15 digits")
    private String phoneNumber;

    @NotBlank
    @Pattern(
            regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[@$!%*?&]).{8,}$",
            message = "Password must be 8+ chars with upper, lower, number, special char"
    )
    private String password;

    @NotNull
    private Role role;

    @NotNull
    private VerificationChannel verificationChannel;

    private String adminRegistrationKey;

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public VerificationChannel getVerificationChannel() {
        return verificationChannel;
    }

    public void setVerificationChannel(VerificationChannel verificationChannel) {
        this.verificationChannel = verificationChannel;
    }

    public String getAdminRegistrationKey() {
        return adminRegistrationKey;
    }

    public void setAdminRegistrationKey(String adminRegistrationKey) {
        this.adminRegistrationKey = adminRegistrationKey;
    }
}
