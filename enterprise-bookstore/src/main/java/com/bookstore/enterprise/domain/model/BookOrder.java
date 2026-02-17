package com.bookstore.enterprise.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class BookOrder {

    private Long id;
    private Long userId;
    private OrderStatus status;
    private PaymentMethod paymentMethod;
    private BigDecimal totalAmount;
    private String shippingAddress;
    private List<OrderItem> items = new ArrayList<>();
    private LocalDateTime createdAt;

    public BookOrder() {
    }

    public BookOrder(
            Long id,
            Long userId,
            OrderStatus status,
            PaymentMethod paymentMethod,
            BigDecimal totalAmount,
            String shippingAddress,
            List<OrderItem> items,
            LocalDateTime createdAt
    ) {
        this.id = id;
        this.userId = userId;
        this.status = status;
        this.paymentMethod = paymentMethod;
        this.totalAmount = totalAmount;
        this.shippingAddress = shippingAddress;
        if (items != null) {
            this.items = items;
        }
        this.createdAt = createdAt;
    }

    public void calculateTotalAmount() {
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItem item : items) {
            item.calculateLineTotal();
            if (item.getLineTotal() != null) {
                total = total.add(item.getLineTotal());
            }
        }
        totalAmount = total;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(String shippingAddress) {
        this.shippingAddress = shippingAddress;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
