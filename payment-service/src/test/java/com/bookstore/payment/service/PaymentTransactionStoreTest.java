package com.bookstore.payment.service;

import com.bookstore.payment.entity.PaymentAttempt;
import com.bookstore.payment.entity.PaymentStatus;
import com.bookstore.payment.entity.PaymentTransaction;
import com.bookstore.payment.repository.PaymentAttemptRepository;
import com.bookstore.payment.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentTransactionStoreTest {
    @Mock PaymentTransactionRepository transactionRepository;
    @Mock PaymentAttemptRepository attemptRepository;

    private PaymentTransactionStore store;

    @BeforeEach
    void setUp() { store = new PaymentTransactionStore(transactionRepository, attemptRepository); }

    @Test
    void replayWithSameAttemptKeyReturnsExistingAttemptWithoutInsertingAnother() {
        PaymentAttempt existing = attempt("order-1", "attempt-1", PaymentStatus.PENDING, 1L);
        when(attemptRepository.findByOrderRequestIdAndAttemptRequestId("order-1", "attempt-1"))
                .thenReturn(Optional.of(existing));
        when(transactionRepository.findByOrderRequestIdForUpdate("order-1"))
                .thenReturn(Optional.of(transaction("order-1", PaymentStatus.PENDING, 1L)));

        PaymentAttempt replay = store.createAttempt("order-1", "attempt-1", "rzp-order-1", new BigDecimal("125.00"));

        assertSame(existing, replay);
        verify(attemptRepository, never()).saveAndFlush(any());
        verify(transactionRepository).findByOrderRequestIdForUpdate("order-1");
    }

    @Test
    void newKeyCannotStartAnotherAttemptWhileLatestAttemptIsPending() {
        PaymentTransaction transaction = transaction("order-1", PaymentStatus.PENDING, 1L);
        PaymentAttempt pending = attempt("order-1", "attempt-1", PaymentStatus.PENDING, 1L);
        when(attemptRepository.findByOrderRequestIdAndAttemptRequestId("order-1", "attempt-2"))
                .thenReturn(Optional.empty());
        when(transactionRepository.findByOrderRequestIdForUpdate("order-1")).thenReturn(Optional.of(transaction));
        when(attemptRepository.findFirstByOrderRequestIdOrderByCreatedAtDescIdDesc("order-1"))
                .thenReturn(Optional.of(pending));

        assertThrows(IllegalStateException.class,
                () -> store.createAttempt("order-1", "attempt-2", "rzp-order-1", new BigDecimal("125.00")));
        verify(attemptRepository, never()).saveAndFlush(any());
    }

    @Test
    void confirmedFailureAllowsOneNewAttemptAndIncrementsAttemptCount() {
        PaymentTransaction transaction = transaction("order-1", PaymentStatus.FAILED, 1L);
        PaymentAttempt failed = attempt("order-1", "attempt-1", PaymentStatus.FAILED, 1L);
        when(attemptRepository.findByOrderRequestIdAndAttemptRequestId("order-1", "attempt-2"))
                .thenReturn(Optional.empty());
        when(transactionRepository.findByOrderRequestIdForUpdate("order-1")).thenReturn(Optional.of(transaction));
        when(attemptRepository.findFirstByOrderRequestIdOrderByCreatedAtDescIdDesc("order-1"))
                .thenReturn(Optional.of(failed));
        when(attemptRepository.saveAndFlush(any(PaymentAttempt.class))).thenAnswer(invocation -> {
            PaymentAttempt saved = invocation.getArgument(0);
            saved.setId(2L);
            return saved;
        });
        when(transactionRepository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentAttempt retry = store.createAttempt("order-1", "attempt-2", "rzp-order-1", new BigDecimal("125.00"));

        assertEquals("attempt-2", retry.getAttemptRequestId());
        assertEquals(PaymentStatus.CREATED, retry.getStatus());
        assertEquals(PaymentStatus.PENDING, transaction.getStatus());
        assertEquals(2, transaction.getAttemptCount());
    }

    @Test
    void duplicateFailureCannotDowngradeSuccessfulAttempt() {
        PaymentTransaction transaction = transaction("order-1", PaymentStatus.SUCCESS, 1L);
        PaymentAttempt success = attempt("order-1", "attempt-1", PaymentStatus.SUCCESS, 1L);
        when(transactionRepository.findByOrderRequestIdForUpdate("order-1")).thenReturn(Optional.of(transaction));
        when(attemptRepository.findByOrderRequestIdAndAttemptRequestId("order-1", "attempt-1"))
                .thenReturn(Optional.of(success));

        PaymentAttempt result = store.markPaymentResult("order-1", "attempt-1", "pay-1", PaymentStatus.FAILED,
                "Delayed failure event", true);

        assertSame(success, result);
        assertEquals(PaymentStatus.SUCCESS, success.getStatus());
        verify(attemptRepository, never()).save(any());
    }

    private PaymentTransaction transaction(String orderId, PaymentStatus status, Long count) {
        PaymentTransaction value = new PaymentTransaction();
        value.setOrderRequestId(orderId);
        value.setCustomerEmail("reader@example.com");
        value.setAmount(new BigDecimal("125.00"));
        value.setPaymentMethod("UPI");
        value.setStatus(status);
        value.setAttemptCount(count.intValue());
        value.setGatewayOrderId("rzp-order-1");
        return value;
    }

    private PaymentAttempt attempt(String orderId, String attemptId, PaymentStatus status, Long id) {
        PaymentAttempt value = new PaymentAttempt();
        value.setId(id);
        value.setOrderRequestId(orderId);
        value.setAttemptRequestId(attemptId);
        value.setGatewayOrderId("rzp-order-1");
        value.setAmount(new BigDecimal("125.00"));
        value.setStatus(status);
        value.setVerificationStatus(status == PaymentStatus.SUCCESS ? "VERIFIED" : "PENDING");
        return value;
    }
}
