package com.bookstore.catalog.controller;

import com.bookstore.catalog.dto.ReviewRequest;
import com.bookstore.catalog.service.ReviewService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/books/{bookId}/reviews")
public class ReviewController {
    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public ResponseEntity<?> list(@PathVariable("bookId") Long bookId,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "5") int size,
                                  @RequestParam(required = false) Integer rating,
                                  @RequestParam(defaultValue = "newest") String sort) {
        try {
            return ResponseEntity.ok(reviewService.list(bookId, page, size, rating, sort, getEmail()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> create(@PathVariable("bookId") Long bookId, @Valid @RequestBody ReviewRequest request,
                                    HttpServletRequest httpRequest) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(reviewService.create(bookId, getEmail(), getDisplayName(),
                    httpRequest.getHeader("Authorization"), request));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PutMapping("/{reviewId}")
    public ResponseEntity<?> update(@PathVariable("bookId") Long bookId, @PathVariable("reviewId") Long reviewId,
                                    @Valid @RequestBody ReviewRequest request) {
        try {
            return ResponseEntity.ok(reviewService.update(bookId, reviewId, getEmail(), request));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
        }
    }

    @DeleteMapping("/{reviewId}")
    public ResponseEntity<?> delete(@PathVariable("bookId") Long bookId, @PathVariable("reviewId") Long reviewId) {
        try {
            reviewService.delete(bookId, reviewId, getEmail());
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
        }
    }

    private String getEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) return null;
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            String email = jwt.getClaimAsString("email");
            return email == null || email.isBlank() ? jwt.getSubject() : email;
        }
        return authentication.getName();
    }

    private String getDisplayName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            String name = jwt.getClaimAsString("name");
            return name == null || name.isBlank() ? jwt.getClaimAsString("given_name") : name;
        }
        return null;
    }
}
