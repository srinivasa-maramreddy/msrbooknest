package com.bookstore.payment.dto;

import jakarta.validation.constraints.NotBlank;

public class MockPaymentRequest {
    @NotBlank
    private String orderRequestId;

    @NotBlank
    private String attemptRequestId;

    @NotBlank
    private String vpa;

    public String getOrderRequestId() { return orderRequestId; }
    public void setOrderRequestId(String orderRequestId) { this.orderRequestId = orderRequestId; }
    public String getAttemptRequestId() { return attemptRequestId; }
    public void setAttemptRequestId(String attemptRequestId) { this.attemptRequestId = attemptRequestId; }
    public String getVpa() { return vpa; }
    public void setVpa(String vpa) { this.vpa = vpa; }
}
