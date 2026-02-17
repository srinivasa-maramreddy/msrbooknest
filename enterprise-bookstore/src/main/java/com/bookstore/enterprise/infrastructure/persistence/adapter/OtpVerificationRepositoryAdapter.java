package com.bookstore.enterprise.infrastructure.persistence.adapter;

import com.bookstore.enterprise.domain.model.OtpVerification;
import com.bookstore.enterprise.domain.model.OtpPurpose;
import com.bookstore.enterprise.domain.model.VerificationChannel;
import com.bookstore.enterprise.domain.repository.OtpVerificationRepository;
import com.bookstore.enterprise.infrastructure.persistence.entity.JpaOtpVerificationEntity;
import com.bookstore.enterprise.infrastructure.persistence.entity.JpaUserEntity;
import com.bookstore.enterprise.infrastructure.persistence.repository.JpaOtpVerificationRepository;
import com.bookstore.enterprise.infrastructure.persistence.repository.JpaUserRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class OtpVerificationRepositoryAdapter implements OtpVerificationRepository {

    private final JpaOtpVerificationRepository jpaOtpVerificationRepository;
    private final JpaUserRepository jpaUserRepository;

    public OtpVerificationRepositoryAdapter(
            JpaOtpVerificationRepository jpaOtpVerificationRepository,
            JpaUserRepository jpaUserRepository
    ) {
        this.jpaOtpVerificationRepository = jpaOtpVerificationRepository;
        this.jpaUserRepository = jpaUserRepository;
    }

    @Override
    public OtpVerification save(OtpVerification verification) {
        JpaOtpVerificationEntity entity = verification.getId() == null
                ? new JpaOtpVerificationEntity()
                : jpaOtpVerificationRepository.findById(verification.getId()).orElse(new JpaOtpVerificationEntity());

        JpaUserEntity userEntity = jpaUserRepository.findById(verification.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found for OTP"));

        entity.setUser(userEntity);
        entity.setChannel(verification.getChannel());
        entity.setPurpose(verification.getPurpose());
        entity.setDestination(verification.getDestination());
        entity.setOtpHash(verification.getOtpHash());
        entity.setExpiresAt(verification.getExpiresAt());
        entity.setVerified(verification.isVerified());
        entity.setAttempts(verification.getAttempts());

        JpaOtpVerificationEntity saved = jpaOtpVerificationRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<OtpVerification> findLatestActiveByDestinationAndPurpose(
            VerificationChannel channel,
            String destination,
            OtpPurpose purpose
    ) {
        return jpaOtpVerificationRepository
                .findTopByChannelAndDestinationAndPurposeAndVerifiedFalseOrderByCreatedAtDesc(
                        channel,
                        destination,
                        purpose
                )
                .map(this::toDomain);
    }

    private OtpVerification toDomain(JpaOtpVerificationEntity entity) {
        return new OtpVerification(
                entity.getId(),
                entity.getUser().getId(),
                entity.getPurpose(),
                entity.getChannel(),
                entity.getDestination(),
                entity.getOtpHash(),
                entity.getExpiresAt(),
                entity.isVerified(),
                entity.getAttempts(),
                entity.getCreatedAt()
        );
    }
}
