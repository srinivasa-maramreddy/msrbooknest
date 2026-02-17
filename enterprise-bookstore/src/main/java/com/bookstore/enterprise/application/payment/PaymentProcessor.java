package com.bookstore.enterprise.application.payment;

import com.bookstore.enterprise.domain.model.PaymentMethod;

import java.math.BigDecimal;

public interface PaymentProcessor {

    PaymentResult charge(PaymentMethod paymentMethod, BigDecimal amount, String reference);
}
