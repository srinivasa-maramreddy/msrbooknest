package com.bookstore.auth.repository;

import com.bookstore.auth.entity.OtpVerification;
import com.bookstore.auth.entity.OtpPurpose;
import com.bookstore.auth.entity.VerificationChannel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Long> {

    Optional<OtpVerification> findTopByPurposeAndChannelAndDestinationAndVerifiedFalseOrderByCreatedAtDesc(
            OtpPurpose purpose,
            VerificationChannel channel,
            String destination
    );

    void deleteByPurposeAndChannelAndDestinationAndVerifiedFalse(
            OtpPurpose purpose,
            VerificationChannel channel,
            String destination
    );

    long countByPurposeAndChannelAndDestinationAndCreatedAtAfter(
            OtpPurpose purpose,
            VerificationChannel channel,
            String destination,
            LocalDateTime createdAt
    );

    void deleteByPurposeAndChannelAndDestinationAndCreatedAtBefore(
            OtpPurpose purpose,
            VerificationChannel channel,
            String destination,
            LocalDateTime createdAt
    );

    boolean existsByUser_IdAndPurposeAndChannelAndVerifiedTrue(
            Long userId,
            OtpPurpose purpose,
            VerificationChannel channel
    );

    void deleteByUser_Id(Long userId);
}
