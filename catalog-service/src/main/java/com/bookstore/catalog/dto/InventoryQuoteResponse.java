package com.bookstore.catalog.dto;

import java.math.BigDecimal;

import java.util.List;

public class InventoryQuoteResponse {
    private List<InventoryBookQuote> items;
    private BigDecimal totalAmount;

    public List<InventoryBookQuote> getItems() {
        return items;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setItems(List<InventoryBookQuote> items) {
        this.items = items;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }
}
