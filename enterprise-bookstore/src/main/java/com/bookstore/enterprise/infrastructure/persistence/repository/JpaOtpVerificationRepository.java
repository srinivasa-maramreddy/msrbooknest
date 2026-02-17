package com.bookstore.enterprise.infrastructure.persistence.repository;

import com.bookstore.enterprise.domain.model.VerificationChannel;
import com.bookstore.enterprise.domain.model.OtpPurpose;
import com.bookstore.enterprise.infrastructure.persistence.entity.JpaOtpVerificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JpaOtpVerificationRepository extends JpaRepository<JpaOtpVerificationEntity, Long> {

    Optional<JpaOtpVerificationEntity> findTopByChannelAndDestinationAndPurposeAndVerifiedFalseOrderByCreatedAtDesc(
            VerificationChannel channel,
            String destination,
            OtpPurpose purpose
    );
}
