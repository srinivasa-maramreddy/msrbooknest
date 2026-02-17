package com.bookstore.enterprise.application.service;

import com.bookstore.enterprise.application.dto.auth.LoginRequest;
import com.bookstore.enterprise.application.dto.auth.RegisterInitiationResponse;
import com.bookstore.enterprise.application.dto.auth.RegisterRequest;
import com.bookstore.enterprise.application.exception.BusinessValidationException;
import com.bookstore.enterprise.application.notification.OtpNotifier;
import com.bookstore.enterprise.application.security.OtpCodeGenerator;
import com.bookstore.enterprise.application.security.TokenProvider;
import com.bookstore.enterprise.application.service.impl.AuthServiceImpl;
import com.bookstore.enterprise.domain.model.AppUser;
import com.bookstore.enterprise.domain.model.OtpPurpose;
import com.bookstore.enterprise.domain.model.OtpVerification;
import com.bookstore.enterprise.domain.model.Role;
import com.bookstore.enterprise.domain.model.VerificationChannel;
import com.bookstore.enterprise.domain.repository.OtpVerificationRepository;
import com.bookstore.enterprise.domain.repository.UserRepository;
import com.bookstore.enterprise.infrastructure.config.AdminSecurityProperties;
import com.bookstore.enterprise.infrastructure.config.OtpProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OtpVerificationRepository otpVerificationRepository;

    @Mock
    private TokenProvider tokenProvider;

    @Mock
    private OtpCodeGenerator otpCodeGenerator;

    @Mock
    private OtpNotifier otpNotifier;

    private PasswordEncoder passwordEncoder;
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();

        AdminSecurityProperties adminProps = new AdminSecurityProperties();
        adminProps.setAdminRegistrationKey("secret-admin-key");

        OtpProperties otpProperties = new OtpProperties();
        otpProperties.setLength(6);
        otpProperties.setExpirationMinutes(10);
        otpProperties.setMaxAttempts(5);

        authService = new AuthServiceImpl(
                userRepository,
                otpVerificationRepository,
                passwordEncoder,
                tokenProvider,
                otpCodeGenerator,
                otpNotifier,
                adminProps,
                otpProperties
        );
    }

    @Test
    void registerAdminShouldFailOnInvalidAdminKey() {
        RegisterRequest request = new RegisterRequest();
        request.setFullName("Admin User");
        request.setEmail("admin@book.com");
        request.setPassword("Pass@1234");
        request.setRole(Role.ADMIN);
        request.setVerificationChannel(VerificationChannel.EMAIL);
        request.setAdminRegistrationKey("wrong-key");

        when(userRepository.existsByEmail("admin@book.com")).thenReturn(false);

        assertThrows(BusinessValidationException.class, () -> authService.register(request));
    }

    @Test
    void registerCustomerShouldSendOtp() {
        RegisterRequest request = new RegisterRequest();
        request.setFullName("Customer User");
        request.setEmail("customer@book.com");
        request.setPassword("Pass@1234");
        request.setRole(Role.CUSTOMER);
        request.setVerificationChannel(VerificationChannel.EMAIL);

        AppUser savedUser = new AppUser(
                1L,
                "Customer User",
                "customer@book.com",
                null,
                passwordEncoder.encode("Pass@1234"),
                Role.CUSTOMER,
                false
        );

        when(userRepository.existsByEmail("customer@book.com")).thenReturn(false);
        when(userRepository.save(any(AppUser.class))).thenReturn(savedUser);
        when(otpCodeGenerator.generateCode()).thenReturn("123456");
        when(otpVerificationRepository.save(any(OtpVerification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RegisterInitiationResponse response = authService.register(request);

        assertEquals(VerificationChannel.EMAIL, response.getVerificationChannel());
        assertEquals(OtpPurpose.REGISTRATION, response.getPurpose());
        verify(otpNotifier).sendOtp(VerificationChannel.EMAIL, "customer@book.com", "123456");
    }

    @Test
    void loginShouldSendOtpChallengeForValidCredentials() {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@book.com");
        request.setPassword("Pass@1234");
        request.setVerificationChannel(VerificationChannel.PHONE);

        AppUser user = new AppUser(
                1L,
                "User",
                "user@book.com",
                "9876543210",
                passwordEncoder.encode("Pass@1234"),
                Role.CUSTOMER,
                true
        );

        when(userRepository.findByEmail("user@book.com")).thenReturn(Optional.of(user));
        when(otpCodeGenerator.generateCode()).thenReturn("654321");
        when(otpVerificationRepository.save(any(OtpVerification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RegisterInitiationResponse response = authService.login(request);

        assertEquals(VerificationChannel.PHONE, response.getVerificationChannel());
        assertEquals(OtpPurpose.LOGIN, response.getPurpose());
        verify(otpNotifier).sendOtp(VerificationChannel.PHONE, "9876543210", "654321");
    }
}
