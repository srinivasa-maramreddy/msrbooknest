package com.bookstore.payment.messaging;

import com.bookstore.events.BookstoreTopics;
import com.bookstore.events.PaymentSucceededEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class PaymentEventPublisher {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public PaymentEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void paymentSucceeded(String orderRequestId, String paymentId) {
        try {
            PaymentSucceededEvent event = new PaymentSucceededEvent(UUID.randomUUID().toString(), 1, Instant.now(),
                    orderRequestId, paymentId, "Razorpay payment captured and verified.");
            kafkaTemplate.send(BookstoreTopics.PAYMENT_SUCCEEDED, orderRequestId,
                    objectMapper.writeValueAsString(event)).get(10, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new IllegalStateException("Payment is verified, but the order update is pending retry.", ex);
        }
    }
}
