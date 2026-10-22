package com.bookstore.payment.service;

import com.bookstore.payment.dto.PaymentRequest;
import com.bookstore.payment.dto.PaymentResponse;
import com.bookstore.payment.dto.RefundRequest;
import com.bookstore.payment.entity.PaymentStatus;
import com.bookstore.payment.entity.PaymentTransaction;
import com.bookstore.payment.repository.PaymentTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;

@Service
@Transactional
public class PaymentService {
    private static final List<String> ALLOWED_METHODS = List.of("UPI", "Credit Card", "Debit Card");

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final MockNpciGateway mockNpciGateway;

    public PaymentService(PaymentTransactionRepository paymentTransactionRepository, MockNpciGateway mockNpciGateway) {
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.mockNpciGateway = mockNpciGateway;
    }

    public PaymentResponse process(PaymentRequest request) {
        PaymentTransaction existing = paymentTransactionRepository.findByOrderRequestId(request.getOrderRequestId())
                .orElse(null);
        if (existing != null) {
            return toResponse(existing);
        }

        if (!ALLOWED_METHODS.contains(request.getPaymentMethod())) {
            throw new IllegalArgumentException("Unsupported payment method");
        }

        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero");
        }

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setOrderRequestId(request.getOrderRequestId());
        transaction.setCustomerEmail(request.getCustomerEmail());
        transaction.setAmount(request.getAmount());
        transaction.setPaymentMethod(request.getPaymentMethod());
        if ("UPI".equals(request.getPaymentMethod())) {
            MockNpciGateway.Result result = mockNpciGateway.collect(request.getPaymentAddress(), request.getAmount());
            transaction.setStatus(result.approved() ? PaymentStatus.SUCCESS : PaymentStatus.FAILED);
            transaction.setProviderReference(result.reference());
            transaction.setMessage(result.message());
        } else {
            transaction.setStatus(PaymentStatus.SUCCESS);
            transaction.setProviderReference("DEMO-CARD-" + UUID.randomUUID());
            transaction.setMessage("Demo card payment approved. No real payment was made.");
        }

        return toResponse(paymentTransactionRepository.save(transaction));
    }

    public PaymentResponse refund(RefundRequest request) {
        PaymentTransaction transaction = paymentTransactionRepository.findByOrderRequestId(request.getOrderRequestId())
                .orElseThrow(() -> new RuntimeException("Payment not found for order request"));

        if (transaction.getStatus() == PaymentStatus.REFUNDED) {
            return toResponse(transaction);
        }

        transaction.setStatus(PaymentStatus.REFUNDED);
        transaction.setMessage(request.getReason() == null || request.getReason().isBlank()
                ? "Refund processed"
                : request.getReason());

        return toResponse(paymentTransactionRepository.save(transaction));
    }

    private PaymentResponse toResponse(PaymentTransaction transaction) {
        PaymentResponse response = new PaymentResponse();
        response.setOrderRequestId(transaction.getOrderRequestId());
        response.setPaymentMethod(transaction.getPaymentMethod());
        response.setAmount(transaction.getAmount());
        response.setStatus(transaction.getStatus().name());
        response.setProviderReference(transaction.getProviderReference());
        response.setMessage(transaction.getMessage());
        return response;
    }
}
