package com.bookstore.auth.service;

import com.bookstore.auth.dto.AuthResponse;
import com.bookstore.auth.dto.LoginRequest;
import com.bookstore.auth.dto.PasswordForgotRequest;
import com.bookstore.auth.dto.PasswordRecoveryResponse;
import com.bookstore.auth.dto.PasswordResetRequest;
import com.bookstore.auth.dto.RegisterCompleteRequest;
import com.bookstore.auth.dto.RegisterEmailOtpRequest;
import com.bookstore.auth.dto.RegisterStartRequest;
import com.bookstore.auth.dto.RegistrationStepResponse;
import com.bookstore.auth.entity.OtpPurpose;
import com.bookstore.auth.entity.Role;
import com.bookstore.auth.entity.RegistrationSession;
import com.bookstore.auth.entity.OtpVerification;
import com.bookstore.auth.entity.VerificationChannel;
import com.bookstore.auth.entity.User;
import com.bookstore.auth.repository.OtpVerificationRepository;
import com.bookstore.auth.repository.RegistrationSessionRepository;
import com.bookstore.auth.repository.UserRepository;
import com.bookstore.auth.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.security.SecureRandom;
import java.util.regex.Pattern;

@Service
@Transactional
public class AuthService {
    private static final SecureRandom OTP_RANDOM = new SecureRandom();
    private static final int OTP_EXPIRY_MINUTES = 10;
    private static final int OTP_MAX_ATTEMPTS = 5;
    private static final int OTP_RESEND_COOLDOWN_SECONDS = 60;
    private static final int OTP_MAX_SENDS_PER_HOUR = 5;
    private static final int REGISTRATION_SESSION_RETENTION_HOURS = 24;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    private static final Pattern INDIAN_MOBILE_PATTERN = Pattern.compile("^[6-9]\\d{9}$");

    private final UserRepository userRepository;
    private final OtpVerificationRepository otpVerificationRepository;
    private final RegistrationSessionRepository registrationSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailOtpService emailOtpService;

    public AuthService(
            UserRepository userRepository,
            OtpVerificationRepository otpVerificationRepository,
            RegistrationSessionRepository registrationSessionRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            EmailOtpService emailOtpService
    ) {
        this.userRepository = userRepository;
        this.otpVerificationRepository = otpVerificationRepository;
        this.registrationSessionRepository = registrationSessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailOtpService = emailOtpService;
    }

    public RegistrationStepResponse startRegistration(RegisterStartRequest request) {
        String name = request.getName() == null ? "" : request.getName().trim();
        if (name.isEmpty()) {
            throw new RuntimeException("Name is required");
        }

        String phoneNumber = normalizePhone(request.getPhoneNumber());
        if (phoneNumber == null) {
            throw new RuntimeException("Valid Indian phone number is required");
        }

        clearStaleUnverifiedUserByPhone(phoneNumber);
        if (userRepository.existsByPhoneNumber(phoneNumber)) {
            throw new RuntimeException("Phone number already exists");
        }

        registrationSessionRepository.deleteByCreatedAtBeforeAndCompletedFalse(
                LocalDateTime.now().minusHours(REGISTRATION_SESSION_RETENTION_HOURS));

        RegistrationSession session = new RegistrationSession();
        session.setName(name);
        session.setPhoneNumber(phoneNumber);
        session.setEmailVerified(false);
        session.setCompleted(false);
        session = registrationSessionRepository.save(session);

        return new RegistrationStepResponse(
                "Details saved. Verify your email to continue.",
                session.getId(),
                maskDestination(phoneNumber, true),
                null
        );
    }

    @Transactional(noRollbackFor = IllegalStateException.class)
    public RegistrationStepResponse sendEmailRegistrationOtp(RegisterEmailOtpRequest request) {
        RegistrationSession session = findActiveSession(request.getRegistrationId());

        String email = normalizeEmail(request.getEmail());
        if (email == null) {
            throw new RuntimeException("Valid email is required");
        }

        clearStaleUnverifiedUserByEmail(email);
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email already exists");
        }

        LocalDateTime now = LocalDateTime.now();
        if (registrationSessionRepository.existsByEmailAndEmailOtpSentAtAfter(
                email, now.minusSeconds(OTP_RESEND_COOLDOWN_SECONDS))) {
            throw new RuntimeException("Please wait one minute before requesting another email code.");
        }
        if (registrationSessionRepository.countByEmailAndEmailOtpSentAtAfter(email, now.minusHours(1))
                >= OTP_MAX_SENDS_PER_HOUR) {
            throw new RuntimeException("Too many email code requests. Try again later.");
        }

        String otpCode = generateOtpCode();
        session.setEmail(email);
        session.setEmailOtpHash(passwordEncoder.encode(otpCode));
        session.setEmailOtpExpiresAt(now.plusMinutes(OTP_EXPIRY_MINUTES));
        session.setEmailOtpSentAt(now);
        session.setEmailOtpAttempts(0);
        session.setEmailVerified(false);

        registrationSessionRepository.save(session);
        emailOtpService.sendOtp(email, otpCode, OtpPurpose.REGISTRATION);

        return new RegistrationStepResponse(
                "A verification code was sent to your email.",
                session.getId(),
                maskDestination(session.getPhoneNumber(), true),
                maskDestination(email, false)
        );
    }

    @Transactional(noRollbackFor = InvalidOtpException.class)
    public AuthResponse completeRegistration(RegisterCompleteRequest request) {
        RegistrationSession session = findActiveSession(request.getRegistrationId());

        String email = normalizeEmail(request.getEmail());
        if (email == null) {
            throw new RuntimeException("Valid email is required");
        }

        if (session.getEmail() == null || !session.getEmail().equals(email)) {
            throw new RuntimeException("Send OTP to this email first");
        }

        clearStaleUnverifiedUserByPhone(session.getPhoneNumber());
        if (userRepository.existsByPhoneNumber(session.getPhoneNumber())) {
            throw new RuntimeException("Phone number already exists");
        }

        clearStaleUnverifiedUserByEmail(email);
        if (userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email already exists");
        }

        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new RuntimeException("Password is required");
        }

        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("Password and confirm password must match");
        }

        if (request.getPassword().length() < 10 || request.getPassword().matches("[A-Za-z0-9]*")) {
            throw new RuntimeException("Password must be at least 10 characters and include a special character");
        }

        verifyEmailOtp(session, request.getEmailOtpCode());

        User user = new User();
        user.setName(session.getName());
        user.setPhoneNumber(session.getPhoneNumber());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.CUSTOMER);
        user.setEnabled(true);
        userRepository.save(user);

        session.setEmailVerified(true);
        session.setCompleted(true);
        registrationSessionRepository.save(session);

        String token = jwtService.generateToken(user.getEmail(), user.getRole().name());
        return new AuthResponse(token, user.getEmail(), user.getRole().name());
    }

    public AuthResponse login(LoginRequest request) {
        String rawIdentifier = request.getIdentifier() != null && !request.getIdentifier().isBlank()
                ? request.getIdentifier()
                : request.getEmail();

        if (rawIdentifier == null || rawIdentifier.isBlank()) {
            throw new RuntimeException("Email or phone number is required");
        }

        User user = findUserByIdentifier(rawIdentifier);
        if (!user.isEnabled()) {
            throw new RuntimeException("Account not verified");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }

        String loginId = resolveLoginId(user);
        String token = jwtService.generateToken(loginId, user.getRole().name());
        return new AuthResponse(token, loginId, user.getRole().name());
    }

    @Transactional(noRollbackFor = IllegalStateException.class)
    public PasswordRecoveryResponse startPasswordRecovery(PasswordForgotRequest request) {
        final String genericMessage = "If an account matches those details, a code has been sent or a recent request is still active.";
        User user;
        try {
            user = findUserByIdentifier(request.getIdentifier());
        } catch (RuntimeException ignored) {
            return new PasswordRecoveryResponse(genericMessage, null);
        }

        if (!user.isEnabled() || user.getEmail() == null || user.getEmail().isBlank()) {
            return new PasswordRecoveryResponse(genericMessage, null);
        }

        String email = normalizeEmail(user.getEmail());
        LocalDateTime now = LocalDateTime.now();
        long recentRequests = otpVerificationRepository.countByPurposeAndChannelAndDestinationAndCreatedAtAfter(
                OtpPurpose.PASSWORD_RESET, VerificationChannel.EMAIL, email, now.minusHours(1));
        boolean sentRecently = otpVerificationRepository.countByPurposeAndChannelAndDestinationAndCreatedAtAfter(
                OtpPurpose.PASSWORD_RESET, VerificationChannel.EMAIL, email,
                now.minusSeconds(OTP_RESEND_COOLDOWN_SECONDS)) > 0;
        if (recentRequests >= OTP_MAX_SENDS_PER_HOUR || sentRecently) {
            return new PasswordRecoveryResponse(genericMessage, null);
        }

        otpVerificationRepository.deleteByPurposeAndChannelAndDestinationAndCreatedAtBefore(
                OtpPurpose.PASSWORD_RESET, VerificationChannel.EMAIL, email, now.minusHours(24));

        String otpCode = generateOtpCode();
        OtpVerification verification = new OtpVerification();
        verification.setUser(user);
        verification.setChannel(VerificationChannel.EMAIL);
        verification.setPurpose(OtpPurpose.PASSWORD_RESET);
        verification.setDestination(email);
        verification.setOtpHash(passwordEncoder.encode(otpCode));
        verification.setExpiresAt(now.plusMinutes(OTP_EXPIRY_MINUTES));
        verification.setVerified(false);
        verification.setAttempts(0);
        otpVerificationRepository.save(verification);

        emailOtpService.sendOtp(email, otpCode, OtpPurpose.PASSWORD_RESET);

        return new PasswordRecoveryResponse(genericMessage, null);
    }

    @Transactional(noRollbackFor = RuntimeException.class)
    public void resetPassword(PasswordResetRequest request) {
        if (request.getNewPassword() == null || request.getNewPassword().length() < 10
                || request.getNewPassword().matches("[A-Za-z0-9]*")) {
            throw new RuntimeException("Password must be at least 10 characters and include a special character");
        }
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("Password and confirm password must match");
        }

        User user = findUserByIdentifier(request.getIdentifier());
        String email = normalizeEmail(user.getEmail());
        OtpVerification verification = otpVerificationRepository
                .findTopByPurposeAndChannelAndDestinationAndVerifiedFalseOrderByCreatedAtDesc(
                        OtpPurpose.PASSWORD_RESET, VerificationChannel.EMAIL, email)
                .orElseThrow(() -> new RuntimeException("Request a new verification code and try again"));

        if (!verification.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Request a new verification code and try again");
        }
        if (verification.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("That verification code has expired. Request a new one.");
        }
        if (verification.getAttempts() >= OTP_MAX_ATTEMPTS) {
            throw new RuntimeException("Too many incorrect codes. Request a new one.");
        }
        if (!passwordEncoder.matches(request.getOtpCode(), verification.getOtpHash())) {
            verification.setAttempts(verification.getAttempts() + 1);
            otpVerificationRepository.save(verification);
            throw new RuntimeException("That verification code is incorrect. Check it and try again.");
        }

        verification.setVerified(true);
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        otpVerificationRepository.save(verification);
        userRepository.save(user);
    }

    private RegistrationSession findActiveSession(String registrationId) {
        if (registrationId == null || registrationId.isBlank()) {
            throw new RuntimeException("registrationId is required");
        }

        RegistrationSession session = registrationSessionRepository.findById(registrationId)
                .orElseThrow(() -> new RuntimeException("Registration session not found"));

        if (session.isCompleted()) {
            throw new RuntimeException("Registration session already completed");
        }
        if (session.getCreatedAt().isBefore(LocalDateTime.now().minusMinutes(30))) {
            throw new RuntimeException("This registration has expired. Start again to receive a new email code.");
        }

        return session;
    }

    private void verifyEmailOtp(RegistrationSession session, String otpCode) {
        if (session.getEmailOtpHash() == null || session.getEmailOtpExpiresAt() == null) {
            throw new InvalidOtpException("Email OTP not requested");
        }

        if (session.getEmailOtpExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidOtpException("Email OTP expired. Request a new OTP.");
        }

        if (session.getEmailOtpAttempts() >= OTP_MAX_ATTEMPTS) {
            throw new InvalidOtpException("Maximum email OTP attempts exceeded. Request a new code.");
        }

        if (otpCode == null || !passwordEncoder.matches(otpCode, session.getEmailOtpHash())) {
            session.setEmailOtpAttempts(session.getEmailOtpAttempts() + 1);
            registrationSessionRepository.save(session);
            throw new InvalidOtpException("Invalid email code. Check it and try again.");
        }

        session.setEmailOtpHash(null);
        session.setEmailOtpExpiresAt(null);
        session.setEmailVerified(true);
        registrationSessionRepository.save(session);
    }

    private User findUserByIdentifier(String rawIdentifier) {
        String identifier = rawIdentifier.trim();

        if (identifier.contains("@")) {
            String email = normalizeEmail(identifier);
            if (email == null) {
                throw new RuntimeException("Invalid credentials");
            }
            return userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Invalid credentials"));
        }

        String phone = normalizePhone(identifier);
        if (phone == null) {
            throw new RuntimeException("Invalid credentials");
        }

        return userRepository.findByPhoneNumber(phone)
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));
    }

    private void clearStaleUnverifiedUserByPhone(String phone) {
        userRepository.findByPhoneNumber(phone).ifPresent(user -> {
            if (!user.isEnabled()) {
                otpVerificationRepository.deleteByUser_Id(user.getId());
                userRepository.deleteById(user.getId());
            }
        });
    }

    private void clearStaleUnverifiedUserByEmail(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            if (!user.isEnabled()) {
                otpVerificationRepository.deleteByUser_Id(user.getId());
                userRepository.deleteById(user.getId());
            }
        });
    }

    private String resolveLoginId(User user) {
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            return user.getEmail();
        }
        if (user.getPhoneNumber() == null || user.getPhoneNumber().isBlank()) {
            throw new RuntimeException("Account has no login identifier");
        }
        return user.getPhoneNumber();
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }

        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new RuntimeException("Invalid email format");
        }
        return normalized;
    }

    private String normalizePhone(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return null;
        }

        String digitsOnly = phoneNumber.replaceAll("[^0-9]", "");

        if (digitsOnly.startsWith("0") && digitsOnly.length() == 11) {
            digitsOnly = digitsOnly.substring(1);
        }

        if (digitsOnly.startsWith("91") && digitsOnly.length() == 12) {
            digitsOnly = digitsOnly.substring(2);
        }

        if (!INDIAN_MOBILE_PATTERN.matcher(digitsOnly).matches()) {
            throw new RuntimeException("Only Indian mobile numbers are allowed (use 10-digit mobile or +91 format)");
        }

        return digitsOnly;
    }

    private String maskDestination(String destination, boolean phone) {
        if (destination == null || destination.isBlank()) {
            return null;
        }

        if (phone) {
            if (destination.length() <= 4) {
                return destination;
            }
            return "******" + destination.substring(destination.length() - 4);
        }

        int atIndex = destination.indexOf('@');
        if (atIndex <= 1) {
            return destination;
        }
        return destination.charAt(0) + "***" + destination.substring(atIndex);
    }

    private String generateOtpCode() {
        int otp = OTP_RANDOM.nextInt(1_000_000);
        return String.format("%06d", otp);
    }

    private static class InvalidOtpException extends RuntimeException {
        private InvalidOtpException(String message) {
            super(message);
        }
    }
}
