package com.bookstore.order.repository;

import com.bookstore.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByCustomerEmailOrderByCreatedAtDesc(String customerEmail);

    Optional<Order> findByOrderRequestId(String orderRequestId);

    List<Order> findAllByOrderByCreatedAtDesc();
}
