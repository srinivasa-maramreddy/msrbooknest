package com.bookstore.catalog.messaging;

import com.bookstore.catalog.service.BookService;
import com.bookstore.events.BookstoreTopics;
import com.bookstore.events.OrderCancelledEvent;
import com.bookstore.events.OrderPaidEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class InventoryReservationListener {
    private final BookService bookService;
    private final ObjectMapper objectMapper;

    public InventoryReservationListener(BookService bookService, ObjectMapper objectMapper) {
        this.bookService = bookService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = BookstoreTopics.ORDER_PAID, groupId = "catalog-inventory-reservations")
    public void onOrderPaid(String payload) throws Exception {
        OrderPaidEvent event = objectMapper.readValue(payload, OrderPaidEvent.class);
        bookService.confirmReservation(event.orderRequestId());
    }

    @KafkaListener(topics = BookstoreTopics.ORDER_CANCELLED, groupId = "catalog-inventory-reservations")
    public void onOrderCancelled(String payload) throws Exception {
        OrderCancelledEvent event = objectMapper.readValue(payload, OrderCancelledEvent.class);
        bookService.releaseReservation(event.orderRequestId());
    }
}
