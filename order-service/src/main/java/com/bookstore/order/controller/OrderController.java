package com.bookstore.order.controller;

import com.bookstore.order.dto.PlaceOrderRequest;
import com.bookstore.order.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<?> placeOrder(@RequestBody PlaceOrderRequest request,
                                        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                        HttpServletRequest httpRequest) {
        String email = getEmail(httpRequest);
        if (email == null) {
            return ResponseEntity.status(401).body(Map.of("error", "JWT token required"));
        }
        String authorizationHeader = httpRequest.getHeader("Authorization");

        try {
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                if (request.getOrderRequestId() != null && !request.getOrderRequestId().isBlank()
                        && !idempotencyKey.equals(request.getOrderRequestId())) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Idempotency-Key must match orderRequestId"));
                }
                request.setOrderRequestId(idempotencyKey);
            }
            return ResponseEntity.status(201).body(orderService.place(email, authorizationHeader, request));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/quote")
    public ResponseEntity<?> checkoutQuote(@RequestBody PlaceOrderRequest request, HttpServletRequest httpRequest) {
        String email = getEmail(httpRequest);
        if (email == null) return ResponseEntity.status(401).body(Map.of("error", "JWT token required"));
        try {
            return ResponseEntity.ok(orderService.quote(httpRequest.getHeader("Authorization"), request.getItems()));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> myOrders(HttpServletRequest request) {
        String email = getEmail(request);
        if (email == null) {
            return ResponseEntity.status(401).body(Map.of("error", "JWT token required"));
        }
        return ResponseEntity.ok(orderService.myOrders(email));
    }

    @GetMapping("/me/reference/{orderRequestId}")
    public ResponseEntity<?> getMyOrderByReference(@PathVariable String orderRequestId, HttpServletRequest request) {
        String email = getEmail(request);
        if (email == null) return ResponseEntity.status(401).body(Map.of("error", "JWT token required"));
        try {
            return ResponseEntity.ok(orderService.getOwnedOrder(email, orderRequestId));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(404).body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/me/review-eligibility/{bookId}")
    public ResponseEntity<?> reviewEligibility(@PathVariable Long bookId, HttpServletRequest request) {
        String email = getEmail(request);
        if (email == null) {
            return ResponseEntity.status(401).body(Map.of("error", "JWT token required"));
        }
        return ResponseEntity.ok(Map.of("eligible", orderService.isEligibleToReview(email, bookId)));
    }

    @GetMapping
    public ResponseEntity<?> allOrders(HttpServletRequest request) {
        if (!isAdmin(request)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin role required"));
        }
        return ResponseEntity.ok(orderService.allOrders());
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable("id") Long id, @RequestParam("status") String status, HttpServletRequest request) {
        if (!isAdmin(request)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin role required"));
        }
        try {
            return ResponseEntity.ok(orderService.updateStatus(id, status));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
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

    private boolean isAdmin(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
