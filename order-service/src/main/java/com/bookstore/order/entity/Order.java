package com.bookstore.order.entity;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String customerEmail;

    @Column(nullable = false, unique = true)
    private String orderRequestId;

    @Column(length = 64)
    private String idempotencyHash;

    @Column(nullable = false)
    private String customerName;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private String paymentMethod;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(precision = 12, scale = 2)
    private BigDecimal shippingAmount = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(length = 24)
    private String paymentStatus = "CREATED";

    private String paymentReference;

    @Column(length = 1000)
    private String failureReason;

    @Column(length = 32)
    private String paymentReconciliationStatus = "NONE";

    @Enumerated(EnumType.STRING)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonManagedReference
    private List<OrderItem> items = new ArrayList<>();

    public Long getId() { return id; }
    public String getCustomerEmail() { return customerEmail; }
    public String getOrderRequestId() { return orderRequestId; }
    public String getIdempotencyHash() { return idempotencyHash; }
    public String getCustomerName() { return customerName; }
    public String getPhone() { return phone; }
    public String getAddress() { return address; }
    public String getPaymentMethod() { return paymentMethod; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getShippingAmount() { return shippingAmount == null ? BigDecimal.ZERO : shippingAmount; }
    public BigDecimal getDiscountAmount() { return discountAmount == null ? BigDecimal.ZERO : discountAmount; }
    public String getPaymentStatus() { return paymentStatus == null ? "CREATED" : paymentStatus; }
    public String getPaymentReference() { return paymentReference; }
    public String getFailureReason() { return failureReason; }
    public String getPaymentReconciliationStatus() { return paymentReconciliationStatus; }
    public OrderStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public List<OrderItem> getItems() { return items; }

    public void setId(Long id) { this.id = id; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }
    public void setOrderRequestId(String orderRequestId) { this.orderRequestId = orderRequestId; }
    public void setIdempotencyHash(String idempotencyHash) { this.idempotencyHash = idempotencyHash; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setAddress(String address) { this.address = address; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public void setShippingAmount(BigDecimal shippingAmount) { this.shippingAmount = shippingAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
    public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public void setPaymentReconciliationStatus(String paymentReconciliationStatus) { this.paymentReconciliationStatus = paymentReconciliationStatus; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setItems(List<OrderItem> items) { this.items = items; }
}
