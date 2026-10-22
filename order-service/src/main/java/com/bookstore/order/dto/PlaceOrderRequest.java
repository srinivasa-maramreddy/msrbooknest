package com.bookstore.order.dto;

import java.util.List;

public class PlaceOrderRequest {
    private String orderRequestId;
    private String customerName;
    private String phone;
    private String address;
    private String paymentMethod;
    private String paymentAddress;
    private List<OrderItemRequest> items;

    public String getOrderRequestId() { return orderRequestId; }
    public String getCustomerName() { return customerName; }
    public String getPhone() { return phone; }
    public String getAddress() { return address; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getPaymentAddress() { return paymentAddress; }
    public List<OrderItemRequest> getItems() { return items; }

    public void setOrderRequestId(String orderRequestId) { this.orderRequestId = orderRequestId; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setAddress(String address) { this.address = address; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public void setPaymentAddress(String paymentAddress) { this.paymentAddress = paymentAddress; }
    public void setItems(List<OrderItemRequest> items) { this.items = items; }
}
