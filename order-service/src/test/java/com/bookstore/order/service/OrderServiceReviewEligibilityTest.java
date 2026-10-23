package com.bookstore.order.service;

import com.bookstore.order.entity.Order;
import com.bookstore.order.entity.OrderItem;
import com.bookstore.order.entity.OrderStatus;
import com.bookstore.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderServiceReviewEligibilityTest {
    @Test
    void onlyDeliveredOrdersGrantReviewEligibilityForAnOwnedBook() {
        OrderRepository repository = mock(OrderRepository.class);
        OrderService service = new OrderService(repository, null, RestClient.builder(), "http://catalog", "http://cart");
        Order delivered = order(OrderStatus.DELIVERED, 41L);
        Order paid = order(OrderStatus.PAID, 42L);
        Order returned = order(OrderStatus.RETURNED, 43L);
        when(repository.findByCustomerEmailOrderByCreatedAtDesc("reader@example.com"))
                .thenReturn(List.of(delivered, paid, returned));

        assertThat(service.isEligibleToReview("reader@example.com", 41L)).isTrue();
        assertThat(service.isEligibleToReview("reader@example.com", 42L)).isFalse();
        assertThat(service.isEligibleToReview("reader@example.com", 43L)).isFalse();
    }

    private Order order(OrderStatus status, Long bookId) {
        Order order = new Order();
        order.setStatus(status);
        OrderItem item = new OrderItem();
        item.setBookId(bookId);
        order.setItems(List.of(item));
        return order;
    }
}
