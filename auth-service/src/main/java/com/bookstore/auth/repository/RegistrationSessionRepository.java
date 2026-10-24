package com.bookstore.auth.repository;

import com.bookstore.auth.entity.RegistrationSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RegistrationSessionRepository extends JpaRepository<RegistrationSession, String> {
    void deleteByPhoneNumberAndCompletedFalse(String phoneNumber);

    void deleteByCreatedAtBeforeAndCompletedFalse(LocalDateTime cutoff);

    Optional<RegistrationSession> findTopByPhoneNumberAndCompletedFalseOrderByCreatedAtDesc(String phoneNumber);

    boolean existsByEmailAndEmailOtpSentAtAfter(String email, LocalDateTime cutoff);

    long countByEmailAndEmailOtpSentAtAfter(String email, LocalDateTime cutoff);
}
