package com.bookstore.enterprise.domain.model;

import java.math.BigDecimal;

public class OrderItem {

    private Long id;
    private Long bookId;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;

    public OrderItem() {
    }

    public OrderItem(Long id, Long bookId, Integer quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
        this.id = id;
        this.bookId = bookId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.lineTotal = lineTotal;
    }

    public void calculateLineTotal() {
        if (unitPrice != null && quantity != null) {
            lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBookId() {
        return bookId;
    }

    public void setBookId(Long bookId) {
        this.bookId = bookId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    public void setLineTotal(BigDecimal lineTotal) {
        this.lineTotal = lineTotal;
    }
}
