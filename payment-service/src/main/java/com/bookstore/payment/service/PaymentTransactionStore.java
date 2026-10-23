package com.bookstore.payment.service;

import com.bookstore.payment.entity.PaymentStatus;
import com.bookstore.payment.entity.PaymentAttempt;
import com.bookstore.payment.entity.PaymentTransaction;
import com.bookstore.payment.repository.PaymentAttemptRepository;
import com.bookstore.payment.repository.PaymentTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class PaymentTransactionStore {
    private final PaymentTransactionRepository repository;
    private final PaymentAttemptRepository attemptRepository;

    public PaymentTransactionStore(PaymentTransactionRepository repository, PaymentAttemptRepository attemptRepository) {
        this.repository = repository;
        this.attemptRepository = attemptRepository;
    }

    @Transactional
    public Registration createOrLoad(String orderRequestId, String idempotencyKey, String customerEmail, BigDecimal amount) {
        PaymentTransaction existing = repository.findByOrderRequestId(orderRequestId).orElse(null);
        if (existing != null) return new Registration(existing, false);
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setOrderRequestId(orderRequestId);
        transaction.setIdempotencyKey(idempotencyKey);
        transaction.setCustomerEmail(customerEmail);
        transaction.setAmount(amount);
        transaction.setPaymentMethod("UPI");
        transaction.setCurrency("INR");
        transaction.setStatus(PaymentStatus.CREATED);
        transaction.setProviderReference("PENDING");
        transaction.setMessage("Payment order is being prepared.");
        transaction.setVerificationStatus("NOT_VERIFIED");
        transaction.setAttemptCount(0);
        return new Registration(repository.saveAndFlush(transaction), true);
    }

    @Transactional
    public PaymentTransaction setGatewayOrder(String orderRequestId, String gatewayOrderId) {
        PaymentTransaction transaction = requiredForUpdate(orderRequestId);
        if (transaction.getGatewayOrderId() == null) {
            transaction.setGatewayOrderId(gatewayOrderId);
            transaction.setProviderReference(gatewayOrderId);
            transaction.setStatus(PaymentStatus.PENDING);
            transaction.setMessage("Secure UPI checkout is ready.");
        }
        return repository.save(transaction);
    }

    @Transactional
    public PaymentTransaction markGatewayOutcome(String orderRequestId, String gatewayPaymentId,
                                                 PaymentStatus status, String message, boolean verified) {
        PaymentTransaction transaction = requiredForUpdate(orderRequestId);
        if (transaction.getStatus() == PaymentStatus.SUCCESS) return transaction;
        if (status == PaymentStatus.SUCCESS) {
            transaction.setStatus(PaymentStatus.SUCCESS);
            transaction.setGatewayPaymentId(gatewayPaymentId);
            transaction.setProviderReference(gatewayPaymentId);
            transaction.setVerificationStatus(verified ? "VERIFIED" : "UNVERIFIED");
            transaction.setPaidAt(LocalDateTime.now());
            transaction.setMessage(message == null ? "Payment captured and verified." : message);
        } else if (status == PaymentStatus.FAILED || status == PaymentStatus.CANCELLED) {
            transaction.setStatus(status);
            transaction.setGatewayPaymentId(gatewayPaymentId);
            transaction.setVerificationStatus(verified ? "VERIFIED" : "UNVERIFIED");
            if (transaction.getStatus() != status) {
                transaction.setAttemptCount((transaction.getAttemptCount() == null ? 0 : transaction.getAttemptCount()) + 1);
            }
            transaction.setMessage(message == null ? "Payment was not completed. You may retry this order." : message);
        } else {
            transaction.setStatus(PaymentStatus.PENDING);
            transaction.setVerificationStatus("PENDING");
            transaction.setMessage(message == null ? "We could not verify your payment yet. Check again shortly." : message);
        }
        return repository.save(transaction);
    }

    @Transactional
    public PaymentAttempt createAttempt(String orderRequestId, String attemptRequestId, String gatewayOrderId, BigDecimal amount) {
        PaymentTransaction transaction = requiredForUpdate(orderRequestId);
        PaymentAttempt existing = findAttempt(orderRequestId, attemptRequestId);
        if (existing != null) return existing;
        if (transaction.getStatus() == PaymentStatus.SUCCESS) {
            throw new IllegalStateException("This order is already paid");
        }
        PaymentAttempt latest = latestAttempt(orderRequestId);
        if (latest != null && latest.getStatus() != PaymentStatus.FAILED && latest.getStatus() != PaymentStatus.CANCELLED) {
            throw new IllegalStateException("The current payment attempt is still processing. Check its status before retrying.");
        }
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setOrderRequestId(orderRequestId);
        attempt.setAttemptRequestId(attemptRequestId);
        attempt.setGatewayOrderId(gatewayOrderId);
        attempt.setAmount(amount);
        attempt.setCurrency("INR");
        attempt.setStatus(PaymentStatus.CREATED);
        attempt.setVerificationStatus("NOT_VERIFIED");
        attempt.setMessage("Secure UPI checkout is ready.");
        PaymentAttempt saved = attemptRepository.saveAndFlush(attempt);
        transaction.setAttemptCount((transaction.getAttemptCount() == null ? 0 : transaction.getAttemptCount()) + 1);
        transaction.setStatus(PaymentStatus.PENDING);
        transaction.setGatewayPaymentId(null);
        transaction.setVerificationStatus("PENDING");
        transaction.setProviderReference(gatewayOrderId);
        transaction.setMessage("Secure UPI checkout is ready.");
        repository.save(transaction);
        return saved;
    }

    @Transactional
    public PaymentAttempt markPaymentResult(String orderRequestId, String attemptRequestId, String gatewayPaymentId,
                                           PaymentStatus status, String message, boolean verified) {
        PaymentTransaction transaction = requiredForUpdate(orderRequestId);
        PaymentAttempt attempt = attemptRepository.findByOrderRequestIdAndAttemptRequestId(orderRequestId, attemptRequestId)
                .orElseThrow(() -> new IllegalArgumentException("Payment attempt was not found for this order"));
        if (attempt.getStatus() == PaymentStatus.SUCCESS) return attempt;
        if (status == PaymentStatus.PENDING && attempt.getStatus() == PaymentStatus.FAILED
                && Objects.equals(attempt.getGatewayPaymentId(), gatewayPaymentId)) return attempt;

        attempt.setGatewayPaymentId(gatewayPaymentId);
        attempt.setStatus(status);
        attempt.setVerificationStatus(verified ? "VERIFIED" : status == PaymentStatus.PENDING ? "PENDING" : "UNVERIFIED");
        attempt.setMessage(message == null ? "Payment status updated." : message);
        PaymentAttempt saved = attemptRepository.save(attempt);

        if (transaction.getStatus() != PaymentStatus.SUCCESS) {
            PaymentAttempt latest = latestAttempt(orderRequestId);
            boolean currentAttempt = latest != null && latest.getId().equals(attempt.getId());
            if (status == PaymentStatus.SUCCESS || currentAttempt) {
                transaction.setStatus(status);
                transaction.setGatewayPaymentId(gatewayPaymentId);
                transaction.setVerificationStatus(saved.getVerificationStatus());
                transaction.setMessage(saved.getMessage());
                transaction.setProviderReference(gatewayPaymentId == null ? transaction.getGatewayOrderId() : gatewayPaymentId);
                if (status == PaymentStatus.SUCCESS) transaction.setPaidAt(LocalDateTime.now());
                repository.save(transaction);
            }
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public PaymentTransaction find(String orderRequestId) {
        return repository.findByOrderRequestId(orderRequestId).orElse(null);
    }

    @Transactional(readOnly = true)
    public PaymentTransaction findByGatewayOrder(String gatewayOrderId) {
        return repository.findByGatewayOrderId(gatewayOrderId).orElse(null);
    }

    @Transactional(readOnly = true)
    public PaymentAttempt findAttempt(String orderRequestId, String attemptRequestId) {
        return attemptRepository.findByOrderRequestIdAndAttemptRequestId(orderRequestId, attemptRequestId).orElse(null);
    }

    @Transactional(readOnly = true)
    public PaymentAttempt findAttemptByGatewayPayment(String gatewayPaymentId) {
        return attemptRepository.findByGatewayPaymentId(gatewayPaymentId).orElse(null);
    }

    @Transactional(readOnly = true)
    public PaymentAttempt latestAttempt(String orderRequestId) {
        return attemptRepository.findFirstByOrderRequestIdOrderByCreatedAtDescIdDesc(orderRequestId).orElse(null);
    }

    private PaymentTransaction requiredForUpdate(String orderRequestId) {
        return repository.findByOrderRequestIdForUpdate(orderRequestId)
                .orElseThrow(() -> new IllegalArgumentException("Payment was not found for this order"));
    }

    public record Registration(PaymentTransaction transaction, boolean created) {}
}
