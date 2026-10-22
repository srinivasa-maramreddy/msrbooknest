package com.bookstore.payment.messaging;

import com.bookstore.events.BookstoreTopics;
import com.bookstore.events.OrderPaymentRequestedEvent;
import com.bookstore.events.PaymentFailedEvent;
import com.bookstore.events.PaymentSucceededEvent;
import com.bookstore.payment.dto.PaymentRequest;
import com.bookstore.payment.dto.PaymentResponse;
import com.bookstore.payment.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class PaymentRequestListener {
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public PaymentRequestListener(PaymentService paymentService, ObjectMapper objectMapper,
                                  KafkaTemplate<String, String> kafkaTemplate) {
        this.paymentService = paymentService;
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = BookstoreTopics.PAYMENT_REQUESTED, groupId = "payment-service")
    public void process(String payload) throws Exception {
        OrderPaymentRequestedEvent event = objectMapper.readValue(payload, OrderPaymentRequestedEvent.class);
        PaymentRequest request = new PaymentRequest();
        request.setOrderRequestId(event.orderRequestId());
        request.setCustomerEmail(event.customerEmail());
        request.setAmount(event.amount());
        request.setPaymentMethod(event.paymentMethod());
        request.setPaymentAddress(event.paymentAddress());

        try {
            PaymentResponse response = paymentService.process(request);
            if ("SUCCESS".equals(response.getStatus())) {
                publish(BookstoreTopics.PAYMENT_SUCCEEDED, event.orderRequestId(), new PaymentSucceededEvent(
                        UUID.randomUUID().toString(), 1, Instant.now(), event.orderRequestId(),
                        response.getProviderReference(), response.getMessage()));
            } else {
                publish(BookstoreTopics.PAYMENT_FAILED, event.orderRequestId(), new PaymentFailedEvent(
                        UUID.randomUUID().toString(), 1, Instant.now(), event.orderRequestId(), response.getMessage()));
            }
        } catch (IllegalArgumentException ex) {
            publish(BookstoreTopics.PAYMENT_FAILED, event.orderRequestId(), new PaymentFailedEvent(
                    UUID.randomUUID().toString(), 1, Instant.now(), event.orderRequestId(), ex.getMessage()));
        }
    }

    private void publish(String topic, String key, Object event) throws Exception {
        kafkaTemplate.send(topic, key, objectMapper.writeValueAsString(event)).get(10, TimeUnit.SECONDS);
    }
}
