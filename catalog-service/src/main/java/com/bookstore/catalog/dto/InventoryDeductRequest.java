package com.bookstore.catalog.dto;

import java.util.List;

public class InventoryDeductRequest {
    private List<InventoryItemRequest> items;

    public List<InventoryItemRequest> getItems() {
        return items;
    }

    public void setItems(List<InventoryItemRequest> items) {
        this.items = items;
    }
}
