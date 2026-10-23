package com.bookstore.order.service;

import com.bookstore.order.dto.OrderItemRequest;
import com.bookstore.order.dto.PlaceOrderRequest;
import com.bookstore.order.entity.Order;
import com.bookstore.order.entity.OrderItem;
import com.bookstore.order.entity.OrderStatus;
import com.bookstore.order.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.TreeMap;

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
        String fingerprint = fingerprint(customerEmail, request);
        Order existing = orderRepository.findByOrderRequestId(request.getOrderRequestId()).orElse(null);
        if (existing != null) {
            return verifyIdempotentReplay(existing, customerEmail, fingerprint);
        }

        List<OrderItemRequest> checkoutItems = resolveItems(authorizationHeader, request);
        InventoryQuoteResponse reservation = reserveInventory(request.getOrderRequestId(), checkoutItems, authorizationHeader);
        if (reservation.getItems() == null || reservation.getItems().isEmpty()) {
            throw new RuntimeException("Inventory reservation returned no items");
        }

        Order order = new Order();
        order.setOrderRequestId(request.getOrderRequestId());
        order.setIdempotencyHash(fingerprint);
        order.setCustomerEmail(customerEmail);
        order.setCustomerName(request.getCustomerName().trim());
        order.setPhone(request.getPhone().trim());
        order.setAddress(request.getAddress().trim());
        order.setPaymentMethod(request.getPaymentMethod());

        BigDecimal subtotal = BigDecimal.ZERO;
        for (InventoryBookQuote quote : reservation.getItems()) {
            OrderItem item = new OrderItem();
            item.setBookId(quote.getBookId());
            item.setTitle(quote.getTitle());
            item.setQuantity(quote.getQuantity());
            item.setUnitPrice(quote.getUnitPrice());
            item.setOrder(order);
            order.getItems().add(item);
            subtotal = subtotal.add(quote.getLineTotal());
        }
        BigDecimal shipping = shippingFor(subtotal);
        order.setShippingAmount(shipping);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setTotalAmount(subtotal.add(shipping));
        order.setPaymentStatus("CREATED");

        // The reservation expires automatically if this local transaction fails after the remote hold.
        Order created;
        try {
            created = workflowService.createPending(order, request.getPaymentAddress());
        } catch (DataIntegrityViolationException duplicate) {
            Order winner = orderRepository.findByOrderRequestId(request.getOrderRequestId()).orElse(null);
            if (winner == null) throw duplicate;
            return verifyIdempotentReplay(winner, customerEmail, fingerprint);
        }
        if (request.getItems() == null || request.getItems().isEmpty()) clearServerCart(authorizationHeader);
        return created;
    }

    public CheckoutQuote quote(String authorizationHeader, List<OrderItemRequest> items) {
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("Your cart is empty");
        List<OrderItemRequest> ownedCart = loadServerCart(authorizationHeader);
        if (!sameQuantities(ownedCart, items)) {
            throw new IllegalArgumentException("Your cart changed. Refresh the checkout before continuing.");
        }
        List<Map<String, Object>> requested = items.stream().map(item -> {
            if (item == null || item.getBookId() == null || item.getQuantity() == null || item.getQuantity() < 1) {
                throw new IllegalArgumentException("Each item needs a valid book and quantity");
            }
            return Map.<String, Object>of("bookId", item.getBookId(), "quantity", item.getQuantity());
        }).toList();
        Map<String, Object> payload = Map.of("items", requested);
        CatalogQuote response = catalogClient.post().uri("/api/books/inventory/quote")
                .header("Authorization", authorizationHeader).body(payload).retrieve().body(CatalogQuote.class);
        if (response == null || response.items == null || response.items.isEmpty()) {
            throw new IllegalStateException("The catalog returned an empty checkout quote");
        }
        BigDecimal subtotal = response.items.stream().map(item -> item.lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal shipping = shippingFor(subtotal);
        return new CheckoutQuote(response.items, subtotal, BigDecimal.ZERO, shipping, subtotal.add(shipping), "INR");
    }

    public Order getOwnedOrder(String email, String orderRequestId) {
        Order order = orderRepository.findByOrderRequestId(orderRequestId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        if (!order.getCustomerEmail().equalsIgnoreCase(email)) throw new IllegalArgumentException("Order not found");
        return order;
    }

    private Order verifyIdempotentReplay(Order existing, String customerEmail, String fingerprint) {
        if (!existing.getCustomerEmail().equalsIgnoreCase(customerEmail)) {
            throw new IllegalArgumentException("Idempotency key belongs to another customer");
        }
        if (existing.getIdempotencyHash() != null && !existing.getIdempotencyHash().equals(fingerprint)) {
            throw new IllegalArgumentException("This checkout key was already used with different order details. Start checkout again.");
        }
        return existing;
    }

    private BigDecimal shippingFor(BigDecimal subtotal) {
        return subtotal.compareTo(new BigDecimal("499.00")) >= 0 ? BigDecimal.ZERO : new BigDecimal("40.00");
    }

    private String fingerprint(String customerEmail, PlaceOrderRequest request) {
        StringBuilder canonical = new StringBuilder();
        append(canonical, customerEmail == null ? "" : customerEmail.trim().toLowerCase());
        append(canonical, request.getCustomerName().trim());
        append(canonical, request.getPhone().trim());
        append(canonical, request.getAddress().trim());
        append(canonical, request.getPaymentMethod().trim().toUpperCase());
        TreeMap<Long, Integer> quantities = new TreeMap<>();
        if (request.getItems() != null) {
            request.getItems().forEach(item -> {
                if (item != null && item.getBookId() != null && item.getQuantity() != null) {
                    quantities.merge(item.getBookId(), item.getQuantity(), Integer::sum);
                }
            });
        }
        quantities.forEach((id, quantity) -> { append(canonical, id.toString()); append(canonical, quantity.toString()); });
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private void append(StringBuilder value, String part) {
        value.append(part.length()).append(':').append(part).append(';');
    }

    public List<Order> myOrders(String email) {
        return orderRepository.findByCustomerEmailOrderByCreatedAtDesc(email);
    }

    public boolean isEligibleToReview(String email, Long bookId) {
        return orderRepository.findByCustomerEmailOrderByCreatedAtDesc(email).stream()
                .filter(order -> order.getStatus() == OrderStatus.DELIVERED)
                .flatMap(order -> order.getItems().stream())
                .anyMatch(item -> bookId.equals(item.getBookId()));
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
        if (request.getOrderRequestId().length() > 80 || !request.getOrderRequestId().equals(request.getOrderRequestId().trim())) {
            throw new IllegalArgumentException("Checkout reference must be 1–80 characters with no surrounding spaces");
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
        if (!"UPI".equalsIgnoreCase(request.getPaymentMethod())) {
            throw new IllegalArgumentException("Only UPI payment is currently supported");
        }
        if (request.getItems() != null && request.getItems().isEmpty()) throw new IllegalArgumentException("Your cart is empty");
    }

    private List<OrderItemRequest> resolveItems(String authorizationHeader, PlaceOrderRequest request) {
        List<OrderItemRequest> ownedItems = loadServerCart(authorizationHeader);
        if (request.getItems() != null && !request.getItems().isEmpty()) {
            if (!sameQuantities(ownedItems, request.getItems())) {
                throw new IllegalArgumentException("Your cart changed. Refresh the checkout before continuing.");
            }
            return ownedItems;
        }
        return ownedItems;
    }

    private List<OrderItemRequest> loadServerCart(String authorizationHeader) {
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

    private boolean sameQuantities(List<OrderItemRequest> left, List<OrderItemRequest> right) {
        return quantities(left).equals(quantities(right));
    }

    private Map<Long, Integer> quantities(List<OrderItemRequest> items) {
        Map<Long, Integer> result = new TreeMap<>();
        if (items == null) return result;
        for (OrderItemRequest item : items) {
            if (item == null || item.getBookId() == null || item.getQuantity() == null || item.getQuantity() < 1) {
                throw new IllegalArgumentException("Each cart item needs a valid book and quantity");
            }
            result.merge(item.getBookId(), item.getQuantity(), Integer::sum);
        }
        return result;
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
        private BigDecimal totalAmount;
        public List<InventoryBookQuote> getItems() { return items; }
        public BigDecimal getTotalAmount() { return totalAmount; }
        public void setItems(List<InventoryBookQuote> items) { this.items = items; }
        public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    }

    public record CheckoutQuote(List<InventoryBookQuote> items, BigDecimal subtotal, BigDecimal discountAmount,
                                BigDecimal deliveryAmount, BigDecimal finalAmount, String currency) {}

    public static class CatalogQuote {
        private List<InventoryBookQuote> items;
        public List<InventoryBookQuote> getItems() { return items; }
        public void setItems(List<InventoryBookQuote> items) { this.items = items; }
    }

    public static class InventoryBookQuote {
        private Long bookId;
        private String title;
        private String imageUrl;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal lineTotal;
        public Long getBookId() { return bookId; }
        public String getTitle() { return title; }
        public String getImageUrl() { return imageUrl; }
        public Integer getQuantity() { return quantity; }
        public BigDecimal getUnitPrice() { return unitPrice; }
        public BigDecimal getLineTotal() { return lineTotal; }
        public void setBookId(Long bookId) { this.bookId = bookId; }
        public void setTitle(String title) { this.title = title; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
        public void setLineTotal(BigDecimal lineTotal) { this.lineTotal = lineTotal; }
    }
}
