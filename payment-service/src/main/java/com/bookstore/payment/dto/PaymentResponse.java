package com.bookstore.payment.dto;

import java.math.BigDecimal;

public class PaymentResponse {
    private String orderRequestId;
    private String paymentMethod;
    private BigDecimal amount;
    private String status;
    private String providerReference;
    private String message;

    public String getOrderRequestId() {
        return orderRequestId;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getStatus() {
        return status;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public String getMessage() {
        return message;
    }

    public void setOrderRequestId(String orderRequestId) {
        this.orderRequestId = orderRequestId;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setProviderReference(String providerReference) {
        this.providerReference = providerReference;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
