package com.bookstore.catalog.repository;

import com.bookstore.catalog.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    Page<Review> findByBookIdAndPublishedTrue(Long bookId, Pageable pageable);
    Page<Review> findByBookIdAndPublishedTrueAndRating(Long bookId, Integer rating, Pageable pageable);
    Optional<Review> findByBookIdAndCustomerEmail(Long bookId, String customerEmail);
    Optional<Review> findByIdAndBookId(Long id, Long bookId);
    long countByBookIdAndPublishedTrue(Long bookId);
    long countByBookIdAndPublishedTrueAndRating(Long bookId, Integer rating);

    @Query("select avg(r.rating) from Review r where r.bookId = :bookId and r.published = true")
    Double averageRating(@Param("bookId") Long bookId);
}
