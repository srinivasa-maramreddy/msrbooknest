package com.bookstore.catalog.service;

import com.bookstore.catalog.dto.ReviewPageResponse;
import com.bookstore.catalog.dto.ReviewRequest;
import com.bookstore.catalog.dto.ReviewResponse;
import com.bookstore.catalog.entity.Review;
import com.bookstore.catalog.repository.BookRepository;
import com.bookstore.catalog.repository.ReviewRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Transactional
public class ReviewService {
    private final ReviewRepository reviewRepository;
    private final BookRepository bookRepository;
    private final RestClient orderClient;

    public ReviewService(ReviewRepository reviewRepository, BookRepository bookRepository,
                         RestClient.Builder restClientBuilder,
                         @Value("${app.services.order-base-url:http://localhost:8083}") String orderBaseUrl) {
        this.reviewRepository = reviewRepository;
        this.bookRepository = bookRepository;
        this.orderClient = restClientBuilder.baseUrl(orderBaseUrl).build();
    }

    @Transactional(readOnly = true)
    public ReviewPageResponse list(Long bookId, int pageNumber, int requestedSize, Integer rating, String sort,
                                   String customerEmail) {
        requireBook(bookId);
        if (rating != null && (rating < 1 || rating > 5)) throw new IllegalArgumentException("Rating filter must be 1–5");
        String sortField = switch (sort == null ? "newest" : sort.toLowerCase()) {
            case "highest" -> "rating";
            case "lowest" -> "rating";
            case "newest" -> "createdAt";
            default -> throw new IllegalArgumentException("Sort must be newest, highest, or lowest");
        };
        Sort.Direction direction = "lowest".equalsIgnoreCase(sort) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sortOrder = Sort.by(direction, sortField).and(Sort.by(Sort.Direction.DESC, "createdAt"));
        int safePage = Math.max(pageNumber, 0);
        int safeSize = Math.max(1, Math.min(requestedSize, 20));
        PageRequest pageable = PageRequest.of(safePage, safeSize, sortOrder);
        Page<Review> result = rating == null
                ? reviewRepository.findByBookIdAndPublishedTrue(bookId, pageable)
                : reviewRepository.findByBookIdAndPublishedTrueAndRating(bookId, rating, pageable);
        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int stars = 5; stars >= 1; stars--) {
            distribution.put(stars, reviewRepository.countByBookIdAndPublishedTrueAndRating(bookId, stars));
        }
        Double average = reviewRepository.averageRating(bookId);
        ReviewResponse mine = customerEmail == null ? null
                : reviewRepository.findByBookIdAndCustomerEmail(bookId, customerEmail)
                        .filter(review -> Boolean.TRUE.equals(review.getPublished())).map(ReviewResponse::from).orElse(null);
        return new ReviewPageResponse(average == null ? 0.0 : Math.round(average * 10.0) / 10.0,
                reviewRepository.countByBookIdAndPublishedTrue(bookId), distribution,
                result.getContent().stream().map(ReviewResponse::from).toList(), result.getNumber(), result.getSize(),
                result.hasNext(), mine);
    }

    public ReviewResponse create(Long bookId, String customerEmail, String reviewerName, String authorization,
                                 ReviewRequest request) {
        requireBook(bookId);
        if (customerEmail == null || customerEmail.isBlank()) throw new IllegalArgumentException("Sign in before writing a review");
        if (reviewRepository.findByBookIdAndCustomerEmail(bookId, customerEmail).isPresent()) {
            throw new IllegalStateException("You have already reviewed this book. Edit your existing review instead.");
        }
        requireDeliveredPurchase(bookId, authorization);
        Review review = new Review();
        review.setBookId(bookId);
        review.setCustomerEmail(customerEmail.toLowerCase());
        review.setReviewerName(cleanName(reviewerName));
        apply(review, request);
        try {
            return ReviewResponse.from(reviewRepository.saveAndFlush(review));
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalStateException("You have already reviewed this book. Edit your existing review instead.");
        }
    }

    public ReviewResponse update(Long bookId, Long reviewId, String customerEmail, ReviewRequest request) {
        Review review = ownedReview(bookId, reviewId, customerEmail);
        apply(review, request);
        review.setUpdatedAt(LocalDateTime.now());
        return ReviewResponse.from(reviewRepository.save(review));
    }

    public void delete(Long bookId, Long reviewId, String customerEmail) {
        reviewRepository.delete(ownedReview(bookId, reviewId, customerEmail));
    }

    private Review ownedReview(Long bookId, Long reviewId, String customerEmail) {
        if (customerEmail == null || customerEmail.isBlank()) throw new IllegalArgumentException("Sign in to manage your review");
        return reviewRepository.findByIdAndBookId(reviewId, bookId)
                .filter(review -> review.getCustomerEmail().equalsIgnoreCase(customerEmail))
                .orElseThrow(() -> new IllegalArgumentException("Review not found"));
    }

    private void apply(Review review, ReviewRequest request) {
        if (request == null || request.getRating() == null || request.getRating() < 1 || request.getRating() > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }
        review.setRating(request.getRating());
        review.setTitle(request.getTitle().trim());
        review.setComment(request.getComment().trim());
    }

    private void requireBook(Long bookId) {
        if (!bookRepository.existsById(bookId)) throw new IllegalArgumentException("Book not found");
    }

    private void requireDeliveredPurchase(Long bookId, String authorization) {
        if (authorization == null || authorization.isBlank()) throw new IllegalArgumentException("Sign in to verify your purchase");
        try {
            ReviewEligibility response = orderClient.get()
                    .uri("/api/orders/me/review-eligibility/{bookId}", bookId)
                    .header("Authorization", authorization)
                    .retrieve().body(ReviewEligibility.class);
            if (response == null || !Boolean.TRUE.equals(response.eligible)) {
                throw new IllegalArgumentException("Only customers with a delivered order for this book can review it");
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Could not verify delivery with the order service. Please try again.");
        }
    }

    private String cleanName(String name) {
        if (name == null || name.isBlank()) return "Verified reader";
        String clean = name.trim().replaceAll("[\\r\\n\\t]", " ");
        return clean.length() > 80 ? clean.substring(0, 80) : clean;
    }

    private static final class ReviewEligibility {
        private Boolean eligible;
        public Boolean getEligible() { return eligible; }
        public void setEligible(Boolean eligible) { this.eligible = eligible; }
    }
}
