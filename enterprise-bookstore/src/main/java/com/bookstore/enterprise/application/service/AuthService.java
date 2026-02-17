package com.bookstore.enterprise.application.service;

import com.bookstore.enterprise.application.dto.auth.AuthResponse;
import com.bookstore.enterprise.application.dto.auth.LoginRequest;
import com.bookstore.enterprise.application.dto.auth.RegisterInitiationResponse;
import com.bookstore.enterprise.application.dto.auth.RegisterRequest;
import com.bookstore.enterprise.application.dto.auth.ResendOtpRequest;
import com.bookstore.enterprise.application.dto.auth.VerifyOtpRequest;

public interface AuthService {

    RegisterInitiationResponse register(RegisterRequest request);

    AuthResponse verifyOtp(VerifyOtpRequest request);

    RegisterInitiationResponse resendOtp(ResendOtpRequest request);

    RegisterInitiationResponse login(LoginRequest request);
}
