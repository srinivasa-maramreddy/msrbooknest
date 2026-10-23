package com.bookstore.cart.repository;

import com.bookstore.cart.entity.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {
    List<WishlistItem> findByCustomerEmailOrderByCreatedAtDesc(String customerEmail);
    Optional<WishlistItem> findByCustomerEmailAndBookId(String customerEmail, Long bookId);
    void deleteByCustomerEmailAndBookId(String customerEmail, Long bookId);
}
