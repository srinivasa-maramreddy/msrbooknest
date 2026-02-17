package com.bookstore.enterprise.application.service.impl;

import com.bookstore.enterprise.application.dto.auth.AuthResponse;
import com.bookstore.enterprise.application.dto.auth.LoginRequest;
import com.bookstore.enterprise.application.dto.auth.RegisterInitiationResponse;
import com.bookstore.enterprise.application.dto.auth.RegisterRequest;
import com.bookstore.enterprise.application.dto.auth.ResendOtpRequest;
import com.bookstore.enterprise.application.dto.auth.VerifyOtpRequest;
import com.bookstore.enterprise.application.exception.BusinessValidationException;
import com.bookstore.enterprise.application.notification.OtpNotifier;
import com.bookstore.enterprise.application.security.OtpCodeGenerator;
import com.bookstore.enterprise.application.security.TokenProvider;
import com.bookstore.enterprise.application.service.AuthService;
import com.bookstore.enterprise.domain.model.AppUser;
import com.bookstore.enterprise.domain.model.OtpPurpose;
import com.bookstore.enterprise.domain.model.OtpVerification;
import com.bookstore.enterprise.domain.model.Role;
import com.bookstore.enterprise.domain.model.VerificationChannel;
import com.bookstore.enterprise.domain.repository.OtpVerificationRepository;
import com.bookstore.enterprise.domain.repository.UserRepository;
import com.bookstore.enterprise.infrastructure.config.AdminSecurityProperties;
import com.bookstore.enterprise.infrastructure.config.OtpProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserRepository userRepository;
    private final OtpVerificationRepository otpVerificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenProvider tokenProvider;
    private final OtpCodeGenerator otpCodeGenerator;
    private final OtpNotifier otpNotifier;
    private final AdminSecurityProperties adminSecurityProperties;
    private final OtpProperties otpProperties;

    public AuthServiceImpl(
            UserRepository userRepository,
            OtpVerificationRepository otpVerificationRepository,
            PasswordEncoder passwordEncoder,
            TokenProvider tokenProvider,
            OtpCodeGenerator otpCodeGenerator,
            OtpNotifier otpNotifier,
            AdminSecurityProperties adminSecurityProperties,
            OtpProperties otpProperties
    ) {
        this.userRepository = userRepository;
        this.otpVerificationRepository = otpVerificationRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.otpCodeGenerator = otpCodeGenerator;
        this.otpNotifier = otpNotifier;
        this.adminSecurityProperties = adminSecurityProperties;
        this.otpProperties = otpProperties;
    }

    @Override
    @Transactional
    public RegisterInitiationResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());
        VerificationChannel verificationChannel = resolveVerificationChannel(request.getVerificationChannel());

        if (userRepository.existsByEmail(email)) {
            throw new BusinessValidationException("Email already registered");
        }

        String phoneNumber = normalizePhoneNumber(request.getPhoneNumber());
        if (phoneNumber != null && userRepository.existsByPhoneNumber(phoneNumber)) {
            throw new BusinessValidationException("Phone number already registered");
        }

        if (request.getRole() == Role.ADMIN) {
            String providedKey = request.getAdminRegistrationKey();
            if (!Objects.equals(providedKey, adminSecurityProperties.getAdminRegistrationKey())) {
                throw new BusinessValidationException("Invalid admin registration key");
            }
        }

        AppUser user = new AppUser(
                null,
                request.getFullName(),
                email,
                phoneNumber,
                passwordEncoder.encode(request.getPassword()),
                request.getRole(),
                false
        );

        AppUser savedUser = userRepository.save(user);
        String destination = resolveDestination(savedUser, verificationChannel);
        sendOtp(savedUser.getId(), OtpPurpose.REGISTRATION, verificationChannel, destination);

        log.info(
                "Registered user id={} email={} role={} verificationChannel={}",
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getRole(),
                verificationChannel
        );

        return new RegisterInitiationResponse(
                "OTP sent successfully. Verify to activate account.",
                maskDestination(destination, verificationChannel),
                verificationChannel,
                OtpPurpose.REGISTRATION
        );
    }

    @Override
    @Transactional
    public AuthResponse verifyOtp(VerifyOtpRequest request) {
        VerificationChannel channel = resolveVerificationChannel(request.getVerificationChannel());
        OtpPurpose purpose = resolvePurpose(request.getPurpose(), OtpPurpose.REGISTRATION);
        String destination = normalizeDestination(channel, request.getDestination());

        OtpVerification otpVerification = otpVerificationRepository
                .findLatestActiveByDestinationAndPurpose(channel, destination, purpose)
                .orElseThrow(() -> new BusinessValidationException("OTP not found. Please request a new OTP."));

        if (otpVerification.isExpired(LocalDateTime.now())) {
            throw new BusinessValidationException("OTP expired. Please request a new OTP.");
        }

        if (otpVerification.getAttempts() >= otpProperties.getMaxAttempts()) {
            throw new BusinessValidationException("Maximum OTP attempts exceeded. Please request a new OTP.");
        }

        if (!passwordEncoder.matches(request.getOtpCode(), otpVerification.getOtpHash())) {
            otpVerification.incrementAttempts();
            otpVerificationRepository.save(otpVerification);
            throw new BusinessValidationException("Invalid OTP code");
        }

        otpVerification.markVerified();
        otpVerificationRepository.save(otpVerification);

        AppUser user = userRepository.findById(otpVerification.getUserId())
                .orElseThrow(() -> new BusinessValidationException("User for OTP not found"));

        if (purpose == OtpPurpose.REGISTRATION) {
            if (!user.isEnabled()) {
                user.setEnabled(true);
                user = userRepository.save(user);
            }
            log.info("User verified and activated email={}", user.getEmail());
        } else if (!user.isEnabled()) {
            throw new BusinessValidationException("Account not verified. Please complete registration OTP first.");
        }

        String token = tokenProvider.generateToken(user);
        if (purpose == OtpPurpose.LOGIN) {
            log.info("Login OTP verified for email={}", user.getEmail());
        }
        return new AuthResponse(token, user.getFullName(), user.getEmail(), user.getRole());
    }

    @Override
    @Transactional
    public RegisterInitiationResponse resendOtp(ResendOtpRequest request) {
        VerificationChannel channel = resolveVerificationChannel(request.getVerificationChannel());
        OtpPurpose purpose = resolvePurpose(request.getPurpose(), OtpPurpose.REGISTRATION);
        String destination = normalizeDestination(channel, request.getDestination());

        AppUser user = findUserByDestination(channel, destination);
        if (purpose == OtpPurpose.REGISTRATION && user.isEnabled()) {
            throw new BusinessValidationException("Account is already verified");
        }
        if (purpose == OtpPurpose.LOGIN && !user.isEnabled()) {
            throw new BusinessValidationException("Account not verified. Please verify registration OTP first.");
        }

        sendOtp(user.getId(), purpose, channel, destination);
        return new RegisterInitiationResponse(
                "A new OTP has been sent.",
                maskDestination(destination, channel),
                channel,
                purpose
        );
    }

    @Override
    @Transactional
    public RegisterInitiationResponse login(LoginRequest request) {
        AppUser user = userRepository.findByEmail(normalizeEmail(request.getEmail()))
                .orElseThrow(() -> new BusinessValidationException("Invalid email or password"));

        if (!user.isEnabled()) {
            throw new BusinessValidationException("Account not verified. Please verify OTP first.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessValidationException("Invalid email or password");
        }

        VerificationChannel channel = resolveVerificationChannel(request.getVerificationChannel());
        String destination = resolveDestination(user, channel);
        sendOtp(user.getId(), OtpPurpose.LOGIN, channel, destination);

        log.info("Login OTP sent email={} channel={}", user.getEmail(), channel);
        return new RegisterInitiationResponse(
                "OTP sent successfully. Verify OTP to complete login.",
                maskDestination(destination, channel),
                channel,
                OtpPurpose.LOGIN
        );
    }

    private void sendOtp(Long userId, OtpPurpose purpose, VerificationChannel channel, String destination) {
        String otpCode = otpCodeGenerator.generateCode();

        OtpVerification otpVerification = new OtpVerification();
        otpVerification.setUserId(userId);
        otpVerification.setPurpose(purpose);
        otpVerification.setChannel(channel);
        otpVerification.setDestination(destination);
        otpVerification.setOtpHash(passwordEncoder.encode(otpCode));
        otpVerification.setExpiresAt(LocalDateTime.now().plusMinutes(otpProperties.getExpirationMinutes()));
        otpVerification.setVerified(false);
        otpVerification.setAttempts(0);

        otpVerificationRepository.save(otpVerification);
        otpNotifier.sendOtp(channel, destination, otpCode);
    }

    private AppUser findUserByDestination(VerificationChannel channel, String destination) {
        if (channel == VerificationChannel.EMAIL) {
            return userRepository.findByEmail(destination)
                    .orElseThrow(() -> new BusinessValidationException("User not found for destination"));
        }

        return userRepository.findByPhoneNumber(destination)
                .orElseThrow(() -> new BusinessValidationException("User not found for destination"));
    }

    private VerificationChannel resolveVerificationChannel(VerificationChannel channel) {
        return channel == null ? VerificationChannel.EMAIL : channel;
    }

    private OtpPurpose resolvePurpose(OtpPurpose purpose, OtpPurpose defaultPurpose) {
        return purpose == null ? defaultPurpose : purpose;
    }

    private String resolveDestination(AppUser user, VerificationChannel channel) {
        if (channel == VerificationChannel.EMAIL) {
            return user.getEmail();
        }

        if (user.getPhoneNumber() == null || user.getPhoneNumber().isBlank()) {
            throw new BusinessValidationException("Phone number is required for PHONE verification");
        }

        return user.getPhoneNumber();
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return null;
        }
        return phoneNumber.replaceAll("\\s+", "");
    }

    private String normalizeDestination(VerificationChannel channel, String destination) {
        if (channel == VerificationChannel.EMAIL) {
            return normalizeEmail(destination);
        }

        String normalizedPhone = normalizePhoneNumber(destination);
        if (normalizedPhone == null || normalizedPhone.isBlank()) {
            throw new BusinessValidationException("Phone number destination is required");
        }
        return normalizedPhone;
    }

    private String maskDestination(String destination, VerificationChannel channel) {
        if (channel == VerificationChannel.EMAIL) {
            int atIndex = destination.indexOf('@');
            if (atIndex <= 1) {
                return destination;
            }
            return destination.charAt(0) + "***" + destination.substring(atIndex);
        }

        if (destination.length() <= 4) {
            return destination;
        }
        return "******" + destination.substring(destination.length() - 4);
    }
}
