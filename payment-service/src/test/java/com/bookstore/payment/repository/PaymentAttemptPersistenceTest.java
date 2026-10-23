package com.bookstore.payment.repository;

import com.bookstore.payment.entity.PaymentAttempt;
import com.bookstore.payment.entity.PaymentStatus;
import com.bookstore.payment.entity.PaymentTransaction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class PaymentAttemptPersistenceTest {
    @Autowired PaymentAttemptRepository attemptRepository;
    @Autowired PaymentTransactionRepository transactionRepository;

    @Test
    void persistsDistinctAttemptsForOneOrderAndEnforcesAttemptIdempotency() {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setOrderRequestId("checkout-1");
        transaction.setIdempotencyKey("checkout-1");
        transaction.setCustomerEmail("reader@example.com");
        transaction.setAmount(new BigDecimal("125.00"));
        transaction.setPaymentMethod("UPI");
        transaction.setStatus(PaymentStatus.PENDING);
        transactionRepository.saveAndFlush(transaction);

        attemptRepository.saveAndFlush(attempt("checkout-1", "attempt-1", "rzp-order-1"));
        attemptRepository.saveAndFlush(attempt("checkout-1", "attempt-2", "rzp-order-1"));

        assertEquals(2, attemptRepository.findAll().size());
        assertEquals("attempt-2", attemptRepository
                .findFirstByOrderRequestIdOrderByCreatedAtDescIdDesc("checkout-1").orElseThrow().getAttemptRequestId());
        assertThrows(DataIntegrityViolationException.class, () -> attemptRepository.saveAndFlush(
                attempt("checkout-1", "attempt-2", "rzp-order-1")));
    }

    private PaymentAttempt attempt(String orderId, String attemptId, String gatewayOrderId) {
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setOrderRequestId(orderId);
        attempt.setAttemptRequestId(attemptId);
        attempt.setGatewayOrderId(gatewayOrderId);
        attempt.setAmount(new BigDecimal("125.00"));
        attempt.setCurrency("INR");
        attempt.setStatus(PaymentStatus.CREATED);
        attempt.setVerificationStatus("NOT_VERIFIED");
        return attempt;
    }
}
