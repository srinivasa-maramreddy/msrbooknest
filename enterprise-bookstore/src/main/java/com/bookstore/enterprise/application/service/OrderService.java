package com.bookstore.enterprise.application.service;

import com.bookstore.enterprise.application.dto.order.CreateOrderRequest;
import com.bookstore.enterprise.application.dto.order.OrderResponse;
import com.bookstore.enterprise.domain.model.OrderStatus;

import java.util.List;

public interface OrderService {

    OrderResponse createOrder(CreateOrderRequest request, String userEmail);

    List<OrderResponse> getOrdersForCurrentUser(String userEmail);

    List<OrderResponse> getAllOrders();

    OrderResponse updateOrderStatus(Long orderId, OrderStatus status);
}
