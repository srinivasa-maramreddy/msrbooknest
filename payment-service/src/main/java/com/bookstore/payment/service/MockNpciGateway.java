package com.bookstore.payment.service;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.math.BigDecimal;

/** Local NPCI/UPI simulator for demos. It never contacts a payment network. */
@Component
public class MockNpciGateway {
    public Result collect(String vpa, BigDecimal amount) {
        String normalizedVpa = vpa == null ? "success@upi" : vpa.trim().toLowerCase();
        if (!normalizedVpa.matches("[a-z0-9._-]{2,}@[a-z0-9.-]{2,}")) {
            return new Result(false, reference("DECLINED"), "Enter a demo UPI ID such as success@upi or fail@upi.");
        }
        if (normalizedVpa.startsWith("fail@")) {
            return new Result(false, reference("DECLINED"), "NPCI demo simulator declined the UPI collect request.");
        }
        return new Result(true, reference("APPROVED"),
                "NPCI demo simulator approved the UPI collect request. No real payment was made.");
    }

    private String reference(String result) {
        return "NPCI-DEMO-" + result + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    public record Result(boolean approved, String reference, String message) { }
}
