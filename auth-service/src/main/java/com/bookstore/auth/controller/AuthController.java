package com.bookstore.auth.controller;

import com.bookstore.auth.dto.AuthResponse;
import com.bookstore.auth.dto.DemoRegisterRequest;
import com.bookstore.auth.dto.LoginRequest;
import com.bookstore.auth.dto.PasswordForgotRequest;
import com.bookstore.auth.dto.PasswordRecoveryResponse;
import com.bookstore.auth.dto.PasswordResetRequest;
import com.bookstore.auth.dto.RegisterCompleteRequest;
import com.bookstore.auth.dto.RegisterEmailOtpRequest;
import com.bookstore.auth.dto.RegisterStartRequest;
import com.bookstore.auth.dto.RegistrationStepResponse;
import com.bookstore.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping({"/api/auth", "/auth"})
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register/start")
    public ResponseEntity<?> startRegistration(@Valid @RequestBody RegisterStartRequest request) {
        try {
            RegistrationStepResponse response = authService.startRegistration(request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/register/email/send-otp")
    public ResponseEntity<?> sendEmailRegistrationOtp(@Valid @RequestBody RegisterEmailOtpRequest request) {
        try {
            RegistrationStepResponse response = authService.sendEmailRegistrationOtp(request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/register/complete")
    public ResponseEntity<?> completeRegistration(@Valid @RequestBody RegisterCompleteRequest request) {
        try {
            AuthResponse response = authService.completeRegistration(request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            AuthResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException ex) {
            return ResponseEntity.status(401).body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/password/forgot")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody PasswordForgotRequest request) {
        try {
            return ResponseEntity.ok(authService.startPasswordRecovery(request));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/password/reset")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        try {
            authService.resetPassword(request);
            return ResponseEntity.ok(Map.of("message", "Password changed. You can now sign in with your new password."));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/demo/register")
    public ResponseEntity<?> registerDemo(@Valid @RequestBody DemoRegisterRequest request) {
        try {
            return ResponseEntity.ok(authService.registerDemo(request));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }
}
