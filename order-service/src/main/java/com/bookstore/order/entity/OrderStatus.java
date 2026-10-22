package com.bookstore.order.entity;

public enum OrderStatus {
    CREATED,
    PENDING_PAYMENT,
    PAID,
    PENDING,
    PAYMENT_FAILED,
    INVENTORY_FAILED,
    PACKED,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    RETURNED
}
