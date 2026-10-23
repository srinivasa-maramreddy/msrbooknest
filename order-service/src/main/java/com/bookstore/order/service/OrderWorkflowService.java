package com.bookstore.order.service;

import com.bookstore.events.BookstoreTopics;
import com.bookstore.events.OrderCancelledEvent;
import com.bookstore.events.OrderPaidEvent;
import com.bookstore.events.PaymentFailedEvent;
import com.bookstore.events.PaymentSucceededEvent;
import com.bookstore.order.entity.Order;
import com.bookstore.order.entity.OrderStatus;
import com.bookstore.order.entity.OutboxEvent;
import com.bookstore.order.repository.OrderRepository;
import com.bookstore.order.repository.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class OrderWorkflowService {
    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OrderWorkflowService(OrderRepository orderRepository, OutboxEventRepository outboxRepository,
                                ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Order createPending(Order order, String paymentAddress) {
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setPaymentStatus("CREATED");
        Order saved = orderRepository.save(order);
        return saved;
    }

    @Transactional
    public void paymentSucceeded(PaymentSucceededEvent event) {
        Order order = orderRepository.findByOrderRequestIdForUpdate(event.orderRequestId()).orElse(null);
        if (order == null) return;
        if (order.getStatus() == OrderStatus.CANCELLED) {
            if (!"SUCCESS".equals(order.getPaymentStatus())) {
                order.setPaymentStatus("SUCCESS");
                order.setPaymentReference(event.providerReference());
                order.setPaymentReconciliationStatus("REFUND_REQUIRED");
                order.setFailureReason("A verified payment arrived after this order was cancelled. Refund reconciliation is required.");
                orderRepository.save(order);
            }
            return;
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) return;
        order.setStatus(OrderStatus.PAID);
        order.setPaymentStatus("SUCCESS");
        order.setPaymentReference(event.providerReference());
        order.setPaymentReconciliationStatus("NONE");
        order.setFailureReason(null);
        orderRepository.save(order);
        enqueue(BookstoreTopics.ORDER_PAID, order.getOrderRequestId(), new OrderPaidEvent(
                UUID.randomUUID().toString(), 1, Instant.now(), order.getOrderRequestId(), event.providerReference()));
    }

    @Transactional
    public void paymentFailed(PaymentFailedEvent event) {
        Order order = orderRepository.findByOrderRequestIdForUpdate(event.orderRequestId()).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PENDING_PAYMENT) return;
        if ("SUCCESS".equals(order.getPaymentStatus())) return;
        order.setPaymentStatus("FAILED");
        order.setFailureReason(event.reason());
        orderRepository.save(order);
    }

    private void enqueue(String topic, String key, Object payload) {
        try {
            OutboxEvent event = new OutboxEvent();
            event.setTopic(topic);
            event.setEventKey(key);
            event.setPayload(objectMapper.writeValueAsString(payload));
            event.setCreatedAt(LocalDateTime.now());
            outboxRepository.save(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize order event", ex);
        }
    }
}
