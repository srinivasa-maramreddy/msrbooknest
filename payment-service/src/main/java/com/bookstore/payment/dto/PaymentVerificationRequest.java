package com.bookstore.payment.dto;

import jakarta.validation.constraints.NotBlank;

public class PaymentVerificationRequest {
    @NotBlank private String orderRequestId;
    @NotBlank private String attemptRequestId;
    @NotBlank private String razorpayOrderId;
    @NotBlank private String razorpayPaymentId;
    @NotBlank private String razorpaySignature;

    public String getOrderRequestId() { return orderRequestId; }
    public String getAttemptRequestId() { return attemptRequestId; }
    public String getRazorpayOrderId() { return razorpayOrderId; }
    public String getRazorpayPaymentId() { return razorpayPaymentId; }
    public String getRazorpaySignature() { return razorpaySignature; }
    public void setOrderRequestId(String orderRequestId) { this.orderRequestId = orderRequestId; }
    public void setAttemptRequestId(String attemptRequestId) { this.attemptRequestId = attemptRequestId; }
    public void setRazorpayOrderId(String razorpayOrderId) { this.razorpayOrderId = razorpayOrderId; }
    public void setRazorpayPaymentId(String razorpayPaymentId) { this.razorpayPaymentId = razorpayPaymentId; }
    public void setRazorpaySignature(String razorpaySignature) { this.razorpaySignature = razorpaySignature; }
}
