package com.bookstore.payment.dto;

import jakarta.validation.constraints.NotBlank;

public class GatewayOrderRequest {
    @NotBlank
    private String orderRequestId;

    @NotBlank
    private String attemptRequestId;

    public String getOrderRequestId() { return orderRequestId; }
    public void setOrderRequestId(String orderRequestId) { this.orderRequestId = orderRequestId; }
    public String getAttemptRequestId() { return attemptRequestId; }
    public void setAttemptRequestId(String attemptRequestId) { this.attemptRequestId = attemptRequestId; }
}
