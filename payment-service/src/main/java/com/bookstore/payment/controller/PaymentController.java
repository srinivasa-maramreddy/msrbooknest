package com.bookstore.payment.controller;

import com.bookstore.payment.dto.GatewayOrderRequest;
import com.bookstore.payment.dto.MockPaymentRequest;
import com.bookstore.payment.dto.PaymentVerificationRequest;
import com.bookstore.payment.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/create")
    public ResponseEntity<?> create(@Valid @RequestBody GatewayOrderRequest request,
                                    @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                    HttpServletRequest httpRequest) {
        String email = getEmail();
        if (email == null) return ResponseEntity.status(401).body(Map.of("error", "JWT token required"));
        try {
            return ResponseEntity.ok(paymentService.createGatewayOrder(email, httpRequest.getHeader("Authorization"), request, idempotencyKey));
        } catch (PaymentService.PaymentSetupException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", ex.getMessage()));
        } catch (PaymentService.PaymentOutcomeUnknownException ex) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("status", "PENDING", "orderRequestId", request.getOrderRequestId(), "error", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", "Payment service is temporarily unavailable."));
        }
    }

    @PostMapping("/mock/collect")
    public ResponseEntity<?> collectMockPayment(@Valid @RequestBody MockPaymentRequest request,
                                                @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                HttpServletRequest httpRequest) {
        String email = getEmail();
        if (email == null) return ResponseEntity.status(401).body(Map.of("error", "JWT token required"));
        try {
            return ResponseEntity.ok(paymentService.collectMockPayment(email,
                    httpRequest.getHeader("Authorization"), request, idempotencyKey));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", "The NPCI demo simulator is temporarily unavailable."));
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verify(@Valid @RequestBody PaymentVerificationRequest request, HttpServletRequest httpRequest) {
        String email = getEmail();
        if (email == null) return ResponseEntity.status(401).body(Map.of("error", "JWT token required"));
        try {
            return ResponseEntity.ok(paymentService.verifyPayment(email, httpRequest.getHeader("Authorization"), request));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("status", "PENDING", "error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("status", "PENDING", "error", "We couldn't verify your payment yet. Your payment status is being checked."));
        }
    }

    @GetMapping("/orders/{orderRequestId}/status")
    public ResponseEntity<?> status(@PathVariable String orderRequestId, HttpServletRequest request) {
        String email = getEmail();
        if (email == null) return ResponseEntity.status(401).body(Map.of("error", "JWT token required"));
        try {
            return ResponseEntity.ok(paymentService.checkStatus(email, request.getHeader("Authorization"), orderRequestId));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(404).body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("status", "PENDING", "error", "We couldn't verify your payment yet. Your payment status is being checked."));
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<?> webhook(@RequestBody byte[] rawBody,
                                     @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
                                     @RequestHeader(value = "X-Razorpay-Event-Id", required = false) String eventId) {
        try {
            PaymentService.WebhookResult result = paymentService.processWebhook(eventId, signature, rawBody);
            return ResponseEntity.ok(Map.of("received", true, "duplicate", result.duplicate(), "message", result.message()));
        } catch (PaymentService.PaymentSetupException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
        } catch (DataIntegrityViolationException duplicate) {
            return ResponseEntity.ok(Map.of("received", true, "duplicate", true));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Webhook processing failed; retry is safe."));
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
}
