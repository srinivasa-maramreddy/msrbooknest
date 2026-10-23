package com.bookstore.cart.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(name = "wishlist_items", uniqueConstraints =
        @UniqueConstraint(name = "uk_wishlist_customer_book", columnNames = {"customer_email", "book_id"}),
        indexes = @Index(name = "idx_wishlist_customer_created", columnList = "customer_email,created_at"))
public class WishlistItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_email", nullable = false, length = 254)
    private String customerEmail;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public String getCustomerEmail() { return customerEmail; }
    public Long getBookId() { return bookId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }
    public void setBookId(Long bookId) { this.bookId = bookId; }
}
