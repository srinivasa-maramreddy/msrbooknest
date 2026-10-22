package com.bookstore.catalog.dto;

public class InventoryItemRequest {
    private Long bookId;
    private Integer quantity;

    public Long getBookId() {
        return bookId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setBookId(Long bookId) {
        this.bookId = bookId;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
