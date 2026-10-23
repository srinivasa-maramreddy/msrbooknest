package com.bookstore.order.service;

import com.bookstore.events.PaymentSucceededEvent;
import com.bookstore.order.entity.Order;
import com.bookstore.order.entity.OrderStatus;
import com.bookstore.order.repository.OrderRepository;
import com.bookstore.order.repository.OutboxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderWorkflowIdempotencyTest {
    @Mock OrderRepository orderRepository;
    @Mock OutboxEventRepository outboxRepository;

    private OrderWorkflowService workflow;

    @BeforeEach
    void setUp() {
        workflow = new OrderWorkflowService(orderRepository, outboxRepository, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void duplicatePaymentSuccessEnqueuesFulfilmentOnlyOnce() {
        Order order = order("order-1", OrderStatus.PENDING_PAYMENT, "CREATED");
        when(orderRepository.findByOrderRequestIdForUpdate("order-1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentSucceededEvent event = paymentSuccess("order-1");
        workflow.paymentSucceeded(event);
        workflow.paymentSucceeded(event);

        assertEquals(OrderStatus.PAID, order.getStatus());
        assertEquals("SUCCESS", order.getPaymentStatus());
        verify(orderRepository, times(1)).save(order);
        verify(outboxRepository, times(1)).save(any());
    }

    @Test
    void lateSuccessForCancelledOrderIsFlaggedForRefundWithoutFulfilment() {
        Order order = order("order-2", OrderStatus.CANCELLED, "CANCELLED");
        when(orderRepository.findByOrderRequestIdForUpdate("order-2")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        workflow.paymentSucceeded(paymentSuccess("order-2"));

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertEquals("SUCCESS", order.getPaymentStatus());
        assertEquals("REFUND_REQUIRED", order.getPaymentReconciliationStatus());
        verify(orderRepository).save(order);
        verifyNoInteractions(outboxRepository);
    }

    private Order order(String id, OrderStatus status, String paymentStatus) {
        Order order = new Order();
        order.setOrderRequestId(id);
        order.setStatus(status);
        order.setPaymentStatus(paymentStatus);
        return order;
    }

    private PaymentSucceededEvent paymentSuccess(String orderId) {
        return new PaymentSucceededEvent("event-1", 1, Instant.now(), orderId, "pay-test-1", "verified");
    }
}
