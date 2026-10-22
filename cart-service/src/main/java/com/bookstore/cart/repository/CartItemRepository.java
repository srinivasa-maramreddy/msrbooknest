package com.bookstore.cart.repository;

import com.bookstore.cart.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findByCustomerEmailOrderByUpdatedAtDesc(String customerEmail);

    Optional<CartItem> findByCustomerEmailAndBookId(String customerEmail, Long bookId);

    void deleteByCustomerEmail(String customerEmail);

    void deleteByCustomerEmailAndBookId(String customerEmail, Long bookId);
}
