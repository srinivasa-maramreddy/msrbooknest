package com.bookstore.catalog.entity;

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
@Table(name = "book_reviews", uniqueConstraints =
        @UniqueConstraint(name = "uk_review_customer_book", columnNames = {"customer_email", "book_id"}),
        indexes = {
                @Index(name = "idx_reviews_book_published", columnList = "book_id,published"),
                @Index(name = "idx_reviews_book_rating", columnList = "book_id,rating")
        })
public class Review {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Column(name = "customer_email", nullable = false, length = 254)
    private String customerEmail;

    @Column(name = "reviewer_name", nullable = false, length = 80)
    private String reviewerName;

    @Column(nullable = false)
    private Integer rating;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 4000)
    private String comment;

    @Column(nullable = false)
    private Boolean published = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public Long getBookId() { return bookId; }
    public String getCustomerEmail() { return customerEmail; }
    public String getReviewerName() { return reviewerName; }
    public Integer getRating() { return rating; }
    public String getTitle() { return title; }
    public String getComment() { return comment; }
    public Boolean getPublished() { return published; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setBookId(Long bookId) { this.bookId = bookId; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }
    public void setReviewerName(String reviewerName) { this.reviewerName = reviewerName; }
    public void setRating(Integer rating) { this.rating = rating; }
    public void setTitle(String title) { this.title = title; }
    public void setComment(String comment) { this.comment = comment; }
    public void setPublished(Boolean published) { this.published = published; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
