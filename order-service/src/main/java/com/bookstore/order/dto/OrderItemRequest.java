package com.bookstore.order.dto;

public class OrderItemRequest {
    private Long bookId;
    private String title;
    private Integer quantity;
    private Double unitPrice;

    public Long getBookId() { return bookId; }
    public String getTitle() { return title; }
    public Integer getQuantity() { return quantity; }
    public Double getUnitPrice() { return unitPrice; }

    public void setBookId(Long bookId) { this.bookId = bookId; }
    public void setTitle(String title) { this.title = title; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public void setUnitPrice(Double unitPrice) { this.unitPrice = unitPrice; }
}
