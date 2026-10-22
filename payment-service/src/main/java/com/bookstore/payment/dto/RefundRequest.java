package com.bookstore.payment.dto;

import jakarta.validation.constraints.NotBlank;

public class RefundRequest {
    @NotBlank
    private String orderRequestId;

    private String reason;

    public String getOrderRequestId() {
        return orderRequestId;
    }

    public String getReason() {
        return reason;
    }

    public void setOrderRequestId(String orderRequestId) {
        this.orderRequestId = orderRequestId;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
