package com.bookstore.payment.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_attempts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_attempt_order_key", columnNames = {"order_request_id", "attempt_request_id"})
})
public class PaymentAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_request_id", nullable = false, length = 80)
    private String orderRequestId;

    @Column(name = "attempt_request_id", nullable = false, length = 80)
    private String attemptRequestId;

    @Column(nullable = false, length = 80)
    private String gatewayOrderId;

    @Column(unique = true, length = 80)
    private String gatewayPaymentId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 8)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(nullable = false, length = 24)
    private String verificationStatus = "PENDING";

    @Column(length = 1000)
    private String message;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void touch() { updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public String getOrderRequestId() { return orderRequestId; }
    public String getAttemptRequestId() { return attemptRequestId; }
    public String getGatewayOrderId() { return gatewayOrderId; }
    public String getGatewayPaymentId() { return gatewayPaymentId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public PaymentStatus getStatus() { return status; }
    public String getVerificationStatus() { return verificationStatus; }
    public String getMessage() { return message; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setId(Long id) { this.id = id; }
    public void setOrderRequestId(String orderRequestId) { this.orderRequestId = orderRequestId; }
    public void setAttemptRequestId(String attemptRequestId) { this.attemptRequestId = attemptRequestId; }
    public void setGatewayOrderId(String gatewayOrderId) { this.gatewayOrderId = gatewayOrderId; }
    public void setGatewayPaymentId(String gatewayPaymentId) { this.gatewayPaymentId = gatewayPaymentId; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public void setCurrency(String currency) { this.currency = currency; }
    public void setStatus(PaymentStatus status) { this.status = status; }
    public void setVerificationStatus(String verificationStatus) { this.verificationStatus = verificationStatus; }
    public void setMessage(String message) { this.message = message; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
