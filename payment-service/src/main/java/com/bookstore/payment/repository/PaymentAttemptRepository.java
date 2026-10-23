package com.bookstore.payment.repository;

import com.bookstore.payment.entity.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {
    Optional<PaymentAttempt> findByOrderRequestIdAndAttemptRequestId(String orderRequestId, String attemptRequestId);
    Optional<PaymentAttempt> findFirstByOrderRequestIdOrderByCreatedAtDescIdDesc(String orderRequestId);
    Optional<PaymentAttempt> findByGatewayPaymentId(String gatewayPaymentId);
}
