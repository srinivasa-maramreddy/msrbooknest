package com.bookstore.enterprise.application.mapper;

import com.bookstore.enterprise.application.dto.order.OrderItemResponse;
import com.bookstore.enterprise.application.dto.order.OrderResponse;
import com.bookstore.enterprise.domain.model.BookOrder;
import com.bookstore.enterprise.domain.model.OrderItem;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class OrderMapper {

    public OrderResponse toResponse(BookOrder order, Map<Long, String> bookTitlesById) {
        OrderResponse response = new OrderResponse();
        response.setId(order.getId());
        response.setStatus(order.getStatus());
        response.setPaymentMethod(order.getPaymentMethod());
        response.setTotalAmount(order.getTotalAmount());
        response.setShippingAddress(order.getShippingAddress());
        response.setCreatedAt(order.getCreatedAt());
        response.setItems(mapItems(order.getItems(), bookTitlesById));
        return response;
    }

    private List<OrderItemResponse> mapItems(List<OrderItem> items, Map<Long, String> bookTitlesById) {
        return items.stream()
                .map(item -> {
                    OrderItemResponse itemResponse = new OrderItemResponse();
                    itemResponse.setBookId(item.getBookId());
                    itemResponse.setBookTitle(bookTitlesById.getOrDefault(item.getBookId(), "Unknown Book"));
                    itemResponse.setQuantity(item.getQuantity());
                    itemResponse.setUnitPrice(item.getUnitPrice());
                    itemResponse.setLineTotal(item.getLineTotal());
                    return itemResponse;
                })
                .collect(Collectors.toList());
    }
}
