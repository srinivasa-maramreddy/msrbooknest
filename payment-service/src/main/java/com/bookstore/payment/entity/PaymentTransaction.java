package com.bookstore.payment.entity;

import java.math.BigDecimal;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_transactions")
public class PaymentTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String orderRequestId;

    @Column(length = 80)
    private String idempotencyKey;

    @Column(nullable = false)
    private String customerEmail;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private String paymentMethod;

    @Column(length = 8)
    private String currency = "INR";

    @Column(unique = true, length = 80)
    private String gatewayOrderId;

    @Column(unique = true, length = 80)
    private String gatewayPaymentId;

    @Column(length = 24)
    private String verificationStatus = "NOT_VERIFIED";

    private Integer attemptCount = 0;

    private LocalDateTime paidAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(length = 160)
    private String providerReference;

    @Column(length = 1000)
    private String message;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void touch() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getOrderRequestId() {
        return orderRequestId;
    }

    public String getIdempotencyKey() { return idempotencyKey; }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public String getCurrency() { return currency; }
    public String getGatewayOrderId() { return gatewayOrderId; }
    public String getGatewayPaymentId() { return gatewayPaymentId; }
    public String getVerificationStatus() { return verificationStatus; }
    public Integer getAttemptCount() { return attemptCount; }
    public LocalDateTime getPaidAt() { return paidAt; }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public String getMessage() {
        return message;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setOrderRequestId(String orderRequestId) {
        this.orderRequestId = orderRequestId;
    }

    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public void setCurrency(String currency) { this.currency = currency; }
    public void setGatewayOrderId(String gatewayOrderId) { this.gatewayOrderId = gatewayOrderId; }
    public void setGatewayPaymentId(String gatewayPaymentId) { this.gatewayPaymentId = gatewayPaymentId; }
    public void setVerificationStatus(String verificationStatus) { this.verificationStatus = verificationStatus; }
    public void setAttemptCount(Integer attemptCount) { this.attemptCount = attemptCount; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public void setProviderReference(String providerReference) {
        this.providerReference = providerReference;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
