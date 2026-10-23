package com.bookstore.order.repository;

import com.bookstore.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByCustomerEmailOrderByCreatedAtDesc(String customerEmail);

    Optional<Order> findByOrderRequestId(String orderRequestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.orderRequestId = :orderRequestId")
    Optional<Order> findByOrderRequestIdForUpdate(@Param("orderRequestId") String orderRequestId);

    List<Order> findAllByOrderByCreatedAtDesc();
}
