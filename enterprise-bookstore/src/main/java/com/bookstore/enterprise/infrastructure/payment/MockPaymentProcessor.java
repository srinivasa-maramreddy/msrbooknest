package com.bookstore.enterprise.infrastructure.payment;

import com.bookstore.enterprise.application.payment.PaymentProcessor;
import com.bookstore.enterprise.application.payment.PaymentResult;
import com.bookstore.enterprise.domain.model.PaymentMethod;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class MockPaymentProcessor implements PaymentProcessor {

    @Override
    public PaymentResult charge(PaymentMethod paymentMethod, BigDecimal amount, String reference) {
        String transactionId = paymentMethod.name() + "-" + UUID.randomUUID();
        String message = "Payment accepted for reference " + reference;
        return new PaymentResult(true, transactionId, message);
    }
}
