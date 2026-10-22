package com.bookstore.order.dto;

import java.math.BigDecimal;

public class OrderItemRequest {
    private Long bookId;
    private String title;
    private Integer quantity;
    private BigDecimal unitPrice;

    public Long getBookId() { return bookId; }
    public String getTitle() { return title; }
    public Integer getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }

    public void setBookId(Long bookId) { this.bookId = bookId; }
    public void setTitle(String title) { this.title = title; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
}
