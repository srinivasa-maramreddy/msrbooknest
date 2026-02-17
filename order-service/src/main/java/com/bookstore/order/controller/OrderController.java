package com.bookstore.order.controller;

import com.bookstore.order.dto.PlaceOrderRequest;
import com.bookstore.order.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<?> placeOrder(@RequestBody PlaceOrderRequest request, HttpServletRequest httpRequest) {
        String email = getEmail(httpRequest);
        if (email == null) {
            return ResponseEntity.status(401).body(Map.of("error", "JWT token required"));
        }

        try {
            return ResponseEntity.ok(orderService.place(email, request));
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
        Object email = request.getAttribute("userEmail");
        return email == null ? null : email.toString();
    }

    private boolean isAdmin(HttpServletRequest request) {
        Object role = request.getAttribute("userRole");
        return role != null && "ADMIN".equalsIgnoreCase(role.toString());
    }
}
