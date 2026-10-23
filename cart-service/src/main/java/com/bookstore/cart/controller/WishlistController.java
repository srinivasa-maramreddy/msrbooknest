package com.bookstore.cart.controller;

import com.bookstore.cart.service.WishlistService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/wishlist")
public class WishlistController {
    private final WishlistService wishlistService;

    public WishlistController(WishlistService wishlistService) {
        this.wishlistService = wishlistService;
    }

    @GetMapping
    public ResponseEntity<?> getWishlist(HttpServletRequest request) {
        String email = getEmail();
        if (email == null) return ResponseEntity.status(401).body(Map.of("error", "JWT token required"));
        return ResponseEntity.ok(wishlistService.getWishlist(email));
    }

    @PostMapping("/items/{bookId}")
    public ResponseEntity<?> add(@PathVariable Long bookId) {
        String email = getEmail();
        if (email == null) return ResponseEntity.status(401).body(Map.of("error", "JWT token required"));
        try {
            return ResponseEntity.ok(wishlistService.add(email, bookId));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @DeleteMapping("/items/{bookId}")
    public ResponseEntity<?> remove(@PathVariable Long bookId) {
        String email = getEmail();
        if (email == null) return ResponseEntity.status(401).body(Map.of("error", "JWT token required"));
        return ResponseEntity.ok(wishlistService.remove(email, bookId));
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
}
