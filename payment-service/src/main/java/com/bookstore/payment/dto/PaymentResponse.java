package com.bookstore.payment.dto;

import java.math.BigDecimal;

public class PaymentResponse {
    private String orderRequestId;
    private String attemptRequestId;
    private String paymentMethod;
    private BigDecimal amount;
    private String status;
    private String providerReference;
    private String message;
    private String gatewayOrderId;
    private String gatewayPaymentId;
    private String currency;
    private String keyId;
    private String verificationStatus;
    private Integer attemptCount;
    private java.time.LocalDateTime paidAt;

    public String getOrderRequestId() {
        return orderRequestId;
    }

    public String getAttemptRequestId() { return attemptRequestId; }

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

    public String getGatewayOrderId() { return gatewayOrderId; }
    public String getGatewayPaymentId() { return gatewayPaymentId; }
    public String getCurrency() { return currency; }
    public String getKeyId() { return keyId; }
    public String getVerificationStatus() { return verificationStatus; }
    public Integer getAttemptCount() { return attemptCount; }
    public java.time.LocalDateTime getPaidAt() { return paidAt; }

    public void setOrderRequestId(String orderRequestId) {
        this.orderRequestId = orderRequestId;
    }

    public void setAttemptRequestId(String attemptRequestId) { this.attemptRequestId = attemptRequestId; }

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

    public void setGatewayOrderId(String gatewayOrderId) { this.gatewayOrderId = gatewayOrderId; }
    public void setGatewayPaymentId(String gatewayPaymentId) { this.gatewayPaymentId = gatewayPaymentId; }
    public void setCurrency(String currency) { this.currency = currency; }
    public void setKeyId(String keyId) { this.keyId = keyId; }
    public void setVerificationStatus(String verificationStatus) { this.verificationStatus = verificationStatus; }
    public void setAttemptCount(Integer attemptCount) { this.attemptCount = attemptCount; }
    public void setPaidAt(java.time.LocalDateTime paidAt) { this.paidAt = paidAt; }
}
