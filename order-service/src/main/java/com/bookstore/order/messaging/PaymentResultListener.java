package com.bookstore.order.messaging;

import com.bookstore.events.BookstoreTopics;
import com.bookstore.events.PaymentFailedEvent;
import com.bookstore.events.PaymentSucceededEvent;
import com.bookstore.order.service.OrderWorkflowService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentResultListener {
    private final OrderWorkflowService workflowService;
    private final ObjectMapper objectMapper;

    public PaymentResultListener(OrderWorkflowService workflowService, ObjectMapper objectMapper) {
        this.workflowService = workflowService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = BookstoreTopics.PAYMENT_SUCCEEDED, groupId = "order-service-payment-results")
    public void onPaymentSucceeded(String payload) throws Exception {
        workflowService.paymentSucceeded(objectMapper.readValue(payload, PaymentSucceededEvent.class));
    }

    @KafkaListener(topics = BookstoreTopics.PAYMENT_FAILED, groupId = "order-service-payment-results")
    public void onPaymentFailed(String payload) throws Exception {
        workflowService.paymentFailed(objectMapper.readValue(payload, PaymentFailedEvent.class));
    }
}
