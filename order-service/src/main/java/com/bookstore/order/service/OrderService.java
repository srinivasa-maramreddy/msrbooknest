package com.bookstore.order.service;

import com.bookstore.order.dto.OrderItemRequest;
import com.bookstore.order.dto.PlaceOrderRequest;
import com.bookstore.order.entity.Order;
import com.bookstore.order.entity.OrderItem;
import com.bookstore.order.entity.OrderStatus;
import com.bookstore.order.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final OrderWorkflowService workflowService;
    private final RestClient catalogClient;
    private final RestClient cartClient;

    public OrderService(OrderRepository orderRepository, OrderWorkflowService workflowService,
                        RestClient.Builder restClientBuilder,
                        @Value("${app.services.catalog-base-url:http://localhost:8082}") String catalogBaseUrl,
                        @Value("${app.services.cart-base-url:http://localhost:8084}") String cartBaseUrl) {
        this.orderRepository = orderRepository;
        this.workflowService = workflowService;
        this.catalogClient = restClientBuilder.baseUrl(catalogBaseUrl).build();
        this.cartClient = restClientBuilder.baseUrl(cartBaseUrl).build();
    }

    public Order place(String customerEmail, String authorizationHeader, PlaceOrderRequest request) {
        validateCheckoutRequest(request);
        Order existing = orderRepository.findByOrderRequestId(request.getOrderRequestId()).orElse(null);
        if (existing != null) {
            if (!existing.getCustomerEmail().equalsIgnoreCase(customerEmail)) {
                throw new RuntimeException("Order request identifier belongs to another customer");
            }
            return existing;
        }

        List<OrderItemRequest> checkoutItems = resolveItems(authorizationHeader, request);
        InventoryQuoteResponse reservation = reserveInventory(request.getOrderRequestId(), checkoutItems, authorizationHeader);
        if (reservation.getItems() == null || reservation.getItems().isEmpty()) {
            throw new RuntimeException("Inventory reservation returned no items");
        }

        Order order = new Order();
        order.setOrderRequestId(request.getOrderRequestId());
        order.setCustomerEmail(customerEmail);
        order.setCustomerName(request.getCustomerName().trim());
        order.setPhone(request.getPhone().trim());
        order.setAddress(request.getAddress().trim());
        order.setPaymentMethod(request.getPaymentMethod());

        BigDecimal total = BigDecimal.ZERO;
        for (InventoryBookQuote quote : reservation.getItems()) {
            OrderItem item = new OrderItem();
            item.setBookId(quote.getBookId());
            item.setTitle(quote.getTitle());
            item.setQuantity(quote.getQuantity());
            item.setUnitPrice(quote.getUnitPrice());
            item.setOrder(order);
            order.getItems().add(item);
            total = total.add(quote.getLineTotal());
        }
        order.setTotalAmount(total);

        // The reservation expires automatically if this local transaction fails after the remote hold.
        Order created = workflowService.createPending(order, request.getPaymentAddress());
        if (request.getItems() == null || request.getItems().isEmpty()) clearServerCart(authorizationHeader);
        return created;
    }

    public List<Order> myOrders(String email) {
        return orderRepository.findByCustomerEmailOrderByCreatedAtDesc(email);
    }

    public List<Order> allOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc();
    }

    public Order updateStatus(Long id, String statusValue) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        OrderStatus status;
        try {
            status = OrderStatus.valueOf(statusValue.toUpperCase());
        } catch (Exception ex) {
            throw new RuntimeException("Invalid status");
        }
        boolean fulfillmentTransition = switch (order.getStatus()) {
            case PAID -> status == OrderStatus.PACKED;
            case PACKED -> status == OrderStatus.SHIPPED;
            case SHIPPED -> status == OrderStatus.DELIVERED;
            case DELIVERED -> status == OrderStatus.RETURNED;
            default -> false;
        };
        if (!fulfillmentTransition) {
            throw new RuntimeException("Checkout statuses are managed by the order workflow; only valid fulfillment transitions are allowed here");
        }
        order.setStatus(status);
        return orderRepository.save(order);
    }

    private void validateCheckoutRequest(PlaceOrderRequest request) {
        if (request == null || request.getOrderRequestId() == null || request.getOrderRequestId().isBlank()) {
            throw new RuntimeException("orderRequestId is required");
        }
        if (request.getCustomerName() == null || request.getCustomerName().isBlank()) {
            throw new RuntimeException("Customer name is required");
        }
        if (request.getPhone() == null || request.getPhone().isBlank()) {
            throw new RuntimeException("Phone is required");
        }
        if (request.getAddress() == null || request.getAddress().isBlank()) {
            throw new RuntimeException("Address is required");
        }
        if (!List.of("UPI", "Credit Card", "Debit Card").contains(request.getPaymentMethod())) {
            throw new RuntimeException("Invalid payment method");
        }
    }

    private List<OrderItemRequest> resolveItems(String authorizationHeader, PlaceOrderRequest request) {
        if (request.getItems() != null && !request.getItems().isEmpty()) return request.getItems();
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new RuntimeException("Authorization header is required to checkout server cart");
        }

        CartItemResponse[] cartItems = cartClient.get().uri("/api/cart")
                .header("Authorization", authorizationHeader).retrieve().body(CartItemResponse[].class);
        if (cartItems == null || cartItems.length == 0) throw new RuntimeException("Cart is empty");
        List<OrderItemRequest> items = new ArrayList<>();
        for (CartItemResponse cartItem : cartItems) {
            OrderItemRequest item = new OrderItemRequest();
            item.setBookId(cartItem.getBookId());
            item.setQuantity(cartItem.getQuantity());
            items.add(item);
        }
        return items;
    }

    private InventoryQuoteResponse reserveInventory(String orderRequestId, List<OrderItemRequest> items,
                                                    String authorizationHeader) {
        List<Map<String, Object>> requested = items.stream().map(item -> {
            Map<String, Object> value = new HashMap<>();
            value.put("bookId", item.getBookId());
            value.put("quantity", item.getQuantity());
            return value;
        }).toList();
        Map<String, Object> payload = Map.of("orderRequestId", orderRequestId, "items", requested);
        InventoryQuoteResponse response = catalogClient.post().uri("/api/books/inventory/reservations")
                .header("Authorization", authorizationHeader).body(payload).retrieve()
                .body(InventoryQuoteResponse.class);
        if (response == null) throw new RuntimeException("Catalog service returned no reservation");
        return response;
    }

    private void clearServerCart(String authorizationHeader) {
        try {
            cartClient.delete().uri("/api/cart").header("Authorization", authorizationHeader)
                    .retrieve().toBodilessEntity();
        } catch (Exception ignored) {
            // The accepted order remains valid if cart cleanup is temporarily unavailable.
        }
    }

    public static class CartItemResponse {
        private Long bookId;
        private Integer quantity;
        public Long getBookId() { return bookId; }
        public Integer getQuantity() { return quantity; }
        public void setBookId(Long bookId) { this.bookId = bookId; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
    }

    public static class InventoryQuoteResponse {
        private List<InventoryBookQuote> items;
        public List<InventoryBookQuote> getItems() { return items; }
        public void setItems(List<InventoryBookQuote> items) { this.items = items; }
    }

    public static class InventoryBookQuote {
        private Long bookId;
        private String title;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal lineTotal;
        public Long getBookId() { return bookId; }
        public String getTitle() { return title; }
        public Integer getQuantity() { return quantity; }
        public BigDecimal getUnitPrice() { return unitPrice; }
        public BigDecimal getLineTotal() { return lineTotal; }
        public void setBookId(Long bookId) { this.bookId = bookId; }
        public void setTitle(String title) { this.title = title; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
        public void setLineTotal(BigDecimal lineTotal) { this.lineTotal = lineTotal; }
    }
}
