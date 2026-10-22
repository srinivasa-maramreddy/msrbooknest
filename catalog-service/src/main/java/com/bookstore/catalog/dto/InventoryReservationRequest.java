package com.bookstore.catalog.dto;

import java.util.List;

public class InventoryReservationRequest {
    private String orderRequestId;
    private List<InventoryItemRequest> items;

    public String getOrderRequestId() { return orderRequestId; }
    public List<InventoryItemRequest> getItems() { return items; }
    public void setOrderRequestId(String orderRequestId) { this.orderRequestId = orderRequestId; }
    public void setItems(List<InventoryItemRequest> items) { this.items = items; }
}
