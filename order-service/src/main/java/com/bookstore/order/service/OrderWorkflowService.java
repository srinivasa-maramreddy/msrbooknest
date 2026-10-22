package com.bookstore.order.service;

import com.bookstore.events.BookstoreTopics;
import com.bookstore.events.OrderCancelledEvent;
import com.bookstore.events.OrderPaidEvent;
import com.bookstore.events.OrderPaymentRequestedEvent;
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
        Order saved = orderRepository.save(order);
        enqueue(BookstoreTopics.PAYMENT_REQUESTED, saved.getOrderRequestId(), new OrderPaymentRequestedEvent(
                UUID.randomUUID().toString(), 1, Instant.now(), saved.getOrderRequestId(), saved.getCustomerEmail(),
                saved.getTotalAmount(), saved.getPaymentMethod(), paymentAddress));
        return saved;
    }

    @Transactional
    public void paymentSucceeded(PaymentSucceededEvent event) {
        Order order = orderRepository.findByOrderRequestId(event.orderRequestId()).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PENDING_PAYMENT) return;
        order.setStatus(OrderStatus.PAID);
        order.setPaymentReference(event.providerReference());
        order.setFailureReason(null);
        orderRepository.save(order);
        enqueue(BookstoreTopics.ORDER_PAID, order.getOrderRequestId(), new OrderPaidEvent(
                UUID.randomUUID().toString(), 1, Instant.now(), order.getOrderRequestId(), event.providerReference()));
    }

    @Transactional
    public void paymentFailed(PaymentFailedEvent event) {
        Order order = orderRepository.findByOrderRequestId(event.orderRequestId()).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PENDING_PAYMENT) return;
        order.setStatus(OrderStatus.PAYMENT_FAILED);
        order.setFailureReason(event.reason());
        orderRepository.save(order);
        enqueue(BookstoreTopics.ORDER_CANCELLED, order.getOrderRequestId(), new OrderCancelledEvent(
                UUID.randomUUID().toString(), 1, Instant.now(), order.getOrderRequestId(), event.reason()));
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
