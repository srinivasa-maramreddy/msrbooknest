package com.bookstore.buyback.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "buyback_payments", uniqueConstraints = @UniqueConstraint(name = "uk_buyback_payment_case", columnNames = "buyback_id"))
public class BuybackPayment {
    @Id
    private String id = UUID.randomUUID().toString();
    @Column(name = "buyback_id", nullable = false, length = 36)
    private String buybackId;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;
    @Column(nullable = false, unique = true, length = 48)
    private String reference;
    @Column(nullable = false)
    private LocalDateTime completedAt = LocalDateTime.now();

    protected BuybackPayment() { }
    public BuybackPayment(String buybackId, BigDecimal amount) {
        this.buybackId = buybackId;
        this.amount = amount;
        this.reference = "MOCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
    public String getId() { return id; }
    public String getBuybackId() { return buybackId; }
    public BigDecimal getAmount() { return amount; }
    public String getReference() { return reference; }
    public LocalDateTime getCompletedAt() { return completedAt; }
}
