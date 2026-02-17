package com.bookstore.enterprise.domain.repository;

import com.bookstore.enterprise.domain.model.OtpVerification;
import com.bookstore.enterprise.domain.model.OtpPurpose;
import com.bookstore.enterprise.domain.model.VerificationChannel;

import java.util.Optional;

public interface OtpVerificationRepository {

    OtpVerification save(OtpVerification verification);

    Optional<OtpVerification> findLatestActiveByDestinationAndPurpose(
            VerificationChannel channel,
            String destination,
            OtpPurpose purpose
    );
}
