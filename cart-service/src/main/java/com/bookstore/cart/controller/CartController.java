package com.bookstore.cart.controller;

import com.bookstore.cart.dto.AddCartItemRequest;
import com.bookstore.cart.dto.UpdateCartItemRequest;
import com.bookstore.cart.service.CartService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/cart")
public class CartController {
    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public ResponseEntity<?> getCart(HttpServletRequest request) {
        String email = getEmail(request);
        if (email == null) {
            return ResponseEntity.status(401).body(Map.of("error", "JWT token required"));
        }
        return ResponseEntity.ok(cartService.getCart(email));
    }

    @PostMapping("/items")
    public ResponseEntity<?> addItem(@Valid @RequestBody AddCartItemRequest request, HttpServletRequest httpRequest) {
        String email = getEmail(httpRequest);
        if (email == null) {
            return ResponseEntity.status(401).body(Map.of("error", "JWT token required"));
        }
        try {
            return ResponseEntity.ok(cartService.addItem(email, request));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PatchMapping("/items/{bookId}")
    public ResponseEntity<?> updateItem(
            @PathVariable("bookId") Long bookId,
            @Valid @RequestBody UpdateCartItemRequest request,
            HttpServletRequest httpRequest
    ) {
        String email = getEmail(httpRequest);
        if (email == null) {
            return ResponseEntity.status(401).body(Map.of("error", "JWT token required"));
        }
        try {
            return ResponseEntity.ok(cartService.updateQuantity(email, bookId, request));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @DeleteMapping("/items/{bookId}")
    public ResponseEntity<?> removeItem(@PathVariable("bookId") Long bookId, HttpServletRequest request) {
        String email = getEmail(request);
        if (email == null) {
            return ResponseEntity.status(401).body(Map.of("error", "JWT token required"));
        }
        return ResponseEntity.ok(cartService.removeItem(email, bookId));
    }

    @DeleteMapping
    public ResponseEntity<?> clearCart(HttpServletRequest request) {
        String email = getEmail(request);
        if (email == null) {
            return ResponseEntity.status(401).body(Map.of("error", "JWT token required"));
        }
        cartService.clearCart(email);
        return ResponseEntity.ok(Map.of("message", "Cart cleared"));
    }

    private String getEmail(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) return null;
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            String email = jwt.getClaimAsString("email");
            return email == null || email.isBlank() ? jwt.getSubject() : email;
        }
        return authentication.getName();
    }
}
