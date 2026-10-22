package com.bookstore.payment.controller;

import com.bookstore.payment.dto.PaymentRequest;
import com.bookstore.payment.dto.RefundRequest;
import com.bookstore.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/process")
    public ResponseEntity<?> process(@Valid @RequestBody PaymentRequest request) {
        try {
            return ResponseEntity.ok(paymentService.process(request));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/refund")
    public ResponseEntity<?> refund(@Valid @RequestBody RefundRequest request) {
        try {
            return ResponseEntity.ok(paymentService.refund(request));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }
}
