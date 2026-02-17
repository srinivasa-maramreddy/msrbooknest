package com.bookstore.enterprise.presentation.controller;

import com.bookstore.enterprise.application.dto.auth.AuthResponse;
import com.bookstore.enterprise.application.dto.auth.LoginRequest;
import com.bookstore.enterprise.application.dto.auth.RegisterInitiationResponse;
import com.bookstore.enterprise.application.dto.auth.RegisterRequest;
import com.bookstore.enterprise.application.dto.auth.ResendOtpRequest;
import com.bookstore.enterprise.application.dto.auth.VerifyOtpRequest;
import com.bookstore.enterprise.application.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterInitiationResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<AuthResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        return ResponseEntity.ok(authService.verifyOtp(request));
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<RegisterInitiationResponse> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        return ResponseEntity.ok(authService.resendOtp(request));
    }

    @PostMapping("/login")
    public ResponseEntity<RegisterInitiationResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
