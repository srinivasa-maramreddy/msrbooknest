package com.bookstore.order.service;

import com.bookstore.order.dto.OrderItemRequest;
import com.bookstore.order.dto.PlaceOrderRequest;
import com.bookstore.order.entity.Order;
import com.bookstore.order.entity.OrderItem;
import com.bookstore.order.entity.OrderStatus;
import com.bookstore.order.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderService {
    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public Order place(String customerEmail, PlaceOrderRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new RuntimeException("Order items are required");
        }

        if (!List.of("UPI", "Credit Card", "Debit Card").contains(request.getPaymentMethod())) {
            throw new RuntimeException("Invalid payment method");
        }

        Order order = new Order();
        order.setCustomerEmail(customerEmail);
        order.setCustomerName(request.getCustomerName());
        order.setPhone(request.getPhone());
        order.setAddress(request.getAddress());
        order.setPaymentMethod(request.getPaymentMethod());
        order.setStatus(OrderStatus.PENDING);

        double total = 0;
        for (OrderItemRequest itemRequest : request.getItems()) {
            OrderItem item = new OrderItem();
            item.setBookId(itemRequest.getBookId());
            item.setTitle(itemRequest.getTitle());
            item.setQuantity(itemRequest.getQuantity());
            item.setUnitPrice(itemRequest.getUnitPrice());
            item.setOrder(order);

            total += itemRequest.getQuantity() * itemRequest.getUnitPrice();
            order.getItems().add(item);
        }

        order.setTotalAmount(total);
        return orderRepository.save(order);
    }

    public List<Order> myOrders(String email) {
        return orderRepository.findByCustomerEmailOrderByCreatedAtDesc(email);
    }

    public List<Order> allOrders() {
        return orderRepository.findAll();
    }

    public Order updateStatus(Long id, String statusValue) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        OrderStatus status;
        try {
            status = OrderStatus.valueOf(statusValue.toUpperCase());
        } catch (Exception ex) {
            throw new RuntimeException("Invalid status");
        }
        order.setStatus(status);
        return orderRepository.save(order);
    }
}
