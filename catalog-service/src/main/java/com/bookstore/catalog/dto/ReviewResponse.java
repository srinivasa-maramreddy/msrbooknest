package com.bookstore.catalog.dto;

import com.bookstore.catalog.entity.Review;

import java.time.LocalDateTime;

public record ReviewResponse(Long id, Integer rating, String title, String comment, String reviewerName,
                             boolean verifiedPurchaser, LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static ReviewResponse from(Review review) {
        return new ReviewResponse(review.getId(), review.getRating(), review.getTitle(), review.getComment(),
                review.getReviewerName(), true, review.getCreatedAt(), review.getUpdatedAt());
    }
}
