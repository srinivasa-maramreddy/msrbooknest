package com.bookstore.cart.dto;

import jakarta.validation.constraints.NotNull;

public class UpdateCartItemRequest {
    @NotNull
    private Integer quantity;

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
