package com.bookstore.payment.service;

import com.bookstore.payment.dto.GatewayOrderRequest;
import com.bookstore.payment.dto.MockPaymentRequest;
import com.bookstore.payment.dto.PaymentResponse;
import com.bookstore.payment.dto.PaymentVerificationRequest;
import com.bookstore.payment.entity.GatewayWebhookEvent;
import com.bookstore.payment.entity.PaymentAttempt;
import com.bookstore.payment.entity.PaymentStatus;
import com.bookstore.payment.entity.PaymentTransaction;
import com.bookstore.payment.messaging.PaymentEventPublisher;
import com.bookstore.payment.repository.GatewayWebhookEventRepository;
import com.bookstore.payment.repository.PaymentTransactionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

@Service
public class PaymentService {
    private final PaymentTransactionRepository paymentRepository;
    private final GatewayWebhookEventRepository webhookRepository;
    private final PaymentTransactionStore store;
    private final RazorpayGatewayClient gateway;
    private final PaymentEventPublisher eventPublisher;
    private final RestClient orderClient;
    private final ObjectMapper objectMapper;
    private final MockNpciGateway mockNpciGateway;

    public PaymentService(PaymentTransactionRepository paymentRepository,
                          GatewayWebhookEventRepository webhookRepository,
                          PaymentTransactionStore store,
                          RazorpayGatewayClient gateway,
                          PaymentEventPublisher eventPublisher,
                          RestClient.Builder restClientBuilder,
                          ObjectMapper objectMapper,
                          MockNpciGateway mockNpciGateway,
                          @Value("${app.services.order-base-url:http://localhost:8083}") String orderBaseUrl,
                          @Value("${app.services.order-connect-timeout-ms:2000}") int orderConnectTimeoutMs,
                          @Value("${app.services.order-read-timeout-ms:5000}") int orderReadTimeoutMs) {
        this.paymentRepository = paymentRepository;
        this.webhookRepository = webhookRepository;
        this.store = store;
        this.gateway = gateway;
        this.eventPublisher = eventPublisher;
        if (orderConnectTimeoutMs <= 0 || orderReadTimeoutMs <= 0) {
            throw new IllegalArgumentException("Order service timeouts must be positive");
        }
        SimpleClientHttpRequestFactory orderRequestFactory = new SimpleClientHttpRequestFactory();
        orderRequestFactory.setConnectTimeout(orderConnectTimeoutMs);
        orderRequestFactory.setReadTimeout(orderReadTimeoutMs);
        this.orderClient = restClientBuilder.clone().baseUrl(orderBaseUrl)
                .requestFactory(orderRequestFactory).build();
        this.objectMapper = objectMapper;
        this.mockNpciGateway = mockNpciGateway;
    }

    public PaymentResponse createGatewayOrder(String customerEmail, String authorization,
                                              GatewayOrderRequest request, String idempotencyKey) {
        if (!gateway.isEnabled()) throw new PaymentSetupException("Razorpay test mode is disabled. Configure server-side test credentials before taking payments.");
        if (request == null || request.getOrderRequestId() == null || request.getOrderRequestId().isBlank()) {
            throw new IllegalArgumentException("Order reference is required");
        }
        String orderRequestId = request.getOrderRequestId();
        String attemptRequestId = request.getAttemptRequestId();
        if (attemptRequestId == null || attemptRequestId.isBlank() || attemptRequestId.length() > 80
                || idempotencyKey == null || !idempotencyKey.equals(attemptRequestId)) {
            throw new IllegalArgumentException("Idempotency-Key must match the payment attempt reference");
        }

        OrderSnapshot order = findOwnedOrder(orderRequestId, authorization);
        if (!"UPI".equalsIgnoreCase(order.paymentMethod)) throw new IllegalArgumentException("Only UPI is supported");
        if (!"PENDING_PAYMENT".equals(order.status)) throw new IllegalStateException("This order is not awaiting payment");
        BigDecimal trustedAmount = money(order.totalAmount);
        PaymentTransaction existing = store.find(orderRequestId);
        if (existing != null) {
            assertPaymentOwnership(existing, customerEmail, trustedAmount);
            if (existing.getGatewayOrderId() != null && existing.getGatewayOrderId().startsWith("NPCI-DEMO-ORDER-")) {
                throw new IllegalStateException("This order already uses the NPCI demo simulator. Continue with its existing payment attempt.");
            }
            if (existing.getStatus() == PaymentStatus.SUCCESS) return toResponse(existing, store.latestAttempt(orderRequestId));
            if (existing.getGatewayOrderId() != null) {
                PaymentAttempt attempt = createAttemptSafely(orderRequestId, attemptRequestId, existing.getGatewayOrderId(), trustedAmount);
                return checkoutResponse(store.find(orderRequestId), attempt);
            }
            // An earlier request may have reached Razorpay but timed out before storing its gateway id.
            // Keep it pending for reconciliation instead of risking a second gateway order.
            return toResponse(existing, store.latestAttempt(orderRequestId));
        }

        PaymentTransactionStore.Registration registration;
        try {
            registration = store.createOrLoad(orderRequestId, orderRequestId, customerEmail,
                    trustedAmount.setScale(2, RoundingMode.UNNECESSARY));
        } catch (DataIntegrityViolationException concurrentRequest) {
            PaymentTransaction winner = store.find(orderRequestId);
            if (winner == null) throw concurrentRequest;
            assertPaymentOwnership(winner, customerEmail, trustedAmount);
            if (winner.getGatewayOrderId() == null) return toResponse(winner, store.latestAttempt(orderRequestId));
            PaymentAttempt attempt = createAttemptSafely(orderRequestId, attemptRequestId, winner.getGatewayOrderId(), trustedAmount);
            return checkoutResponse(store.find(orderRequestId), attempt);
        }
        if (!registration.created()) {
            PaymentTransaction winner = registration.transaction();
            assertPaymentOwnership(winner, customerEmail, trustedAmount);
            if (winner.getGatewayOrderId() == null) return toResponse(winner, store.latestAttempt(orderRequestId));
            PaymentAttempt attempt = createAttemptSafely(orderRequestId, attemptRequestId, winner.getGatewayOrderId(), trustedAmount);
            return checkoutResponse(store.find(orderRequestId), attempt);
        }

        try {
            JsonNode created = gateway.createOrder(trustedAmount, orderRequestId, String.valueOf(order.id));
            String gatewayOrderId = created == null ? null : text(created, "id");
            if (gatewayOrderId == null || gatewayOrderId.isBlank()) {
                PaymentTransaction pending = store.markGatewayOutcome(orderRequestId, null, PaymentStatus.PENDING,
                        "Razorpay did not return an order reference. Check payment status before retrying.", false);
                return toResponse(pending, store.latestAttempt(orderRequestId));
            }
            store.setGatewayOrder(orderRequestId, gatewayOrderId);
            PaymentAttempt attempt = createAttemptSafely(orderRequestId, attemptRequestId, gatewayOrderId, trustedAmount);
            return checkoutResponse(store.find(orderRequestId), attempt);
        } catch (Exception ex) {
            store.markGatewayOutcome(orderRequestId, null, PaymentStatus.PENDING,
                    "We could not confirm whether the payment order was created. Check status before retrying.", false);
            throw new PaymentOutcomeUnknownException("We couldn't verify the payment setup yet. Your order remains pending; check status before trying again.", ex);
        }
    }

    public PaymentResponse collectMockPayment(String customerEmail, String authorization,
                                              MockPaymentRequest request, String idempotencyKey) {
        if (request == null || request.getOrderRequestId() == null || request.getOrderRequestId().isBlank()) {
            throw new IllegalArgumentException("Order reference is required");
        }
        String orderRequestId = request.getOrderRequestId();
        String attemptRequestId = request.getAttemptRequestId();
        if (attemptRequestId == null || attemptRequestId.isBlank() || attemptRequestId.length() > 80
                || idempotencyKey == null || !idempotencyKey.equals(attemptRequestId)) {
            throw new IllegalArgumentException("Idempotency-Key must match the payment attempt reference");
        }

        OrderSnapshot order = findOwnedOrder(orderRequestId, authorization);
        if (!"UPI".equalsIgnoreCase(order.paymentMethod)) throw new IllegalArgumentException("Only UPI is supported");
        BigDecimal trustedAmount = money(order.totalAmount);

        PaymentTransaction transaction = store.find(orderRequestId);
        if (transaction == null) {
            if (!"PENDING_PAYMENT".equals(order.status)) throw new IllegalStateException("This order is not awaiting payment");
            try {
                PaymentTransactionStore.Registration registration = store.createOrLoad(
                        orderRequestId, orderRequestId, customerEmail, trustedAmount.setScale(2, RoundingMode.UNNECESSARY));
                transaction = registration.transaction();
            } catch (DataIntegrityViolationException concurrentRequest) {
                transaction = store.find(orderRequestId);
                if (transaction == null) throw concurrentRequest;
                assertPaymentOwnership(transaction, customerEmail, trustedAmount);
            }
        }

        assertPaymentOwnership(transaction, customerEmail, trustedAmount);
        if (transaction.getStatus() == PaymentStatus.SUCCESS) {
            PaymentAttempt completed = store.latestAttempt(orderRequestId);
            if (completed != null && completed.getGatewayOrderId().startsWith("NPCI-DEMO-ORDER-")) {
                eventPublisher.paymentSucceeded(orderRequestId, transaction.getGatewayPaymentId(), transaction.getMessage());
                return toResponse(transaction, completed);
            }
            throw new IllegalStateException("This order has already been paid through another payment flow");
        }
        if (transaction.getGatewayOrderId() != null && !transaction.getGatewayOrderId().startsWith("NPCI-DEMO-ORDER-")) {
            throw new IllegalStateException("This order already has a payment attempt with another provider");
        }

        MockNpciGateway.Result result = mockNpciGateway.collect(request.getVpa(), trustedAmount);
        PaymentStatus status = result.approved() ? PaymentStatus.SUCCESS : PaymentStatus.FAILED;
        String mockOrderId = transaction.getGatewayOrderId();
        if (mockOrderId == null) {
            mockOrderId = "NPCI-DEMO-ORDER-" + java.util.UUID.randomUUID().toString().replace("-", "");
        }
        PaymentTransactionStore.MockPaymentResult saved = store.recordMockPayment(orderRequestId, attemptRequestId,
                mockOrderId, result.reference(), trustedAmount, status, result.message());
        if (saved.transaction().getStatus() == PaymentStatus.SUCCESS) {
            eventPublisher.paymentSucceeded(orderRequestId, saved.transaction().getGatewayPaymentId(), saved.transaction().getMessage());
        }
        return toResponse(saved.transaction(), saved.attempt());
    }

    public PaymentResponse verifyPayment(String customerEmail, String authorization, PaymentVerificationRequest request) {
        OrderSnapshot order = findOwnedOrder(request.getOrderRequestId(), authorization);
        PaymentTransaction transaction = store.find(request.getOrderRequestId());
        if (transaction == null) throw new IllegalArgumentException("Payment was not created for this order");
        assertPaymentOwnership(transaction, customerEmail, money(order.totalAmount));
        if (transaction.getGatewayOrderId() != null && transaction.getGatewayOrderId().startsWith("NPCI-DEMO-ORDER-")) {
            throw new IllegalStateException("This order uses the NPCI demo simulator and cannot be verified as a Razorpay payment");
        }
        PaymentAttempt attempt = store.findAttempt(request.getOrderRequestId(), request.getAttemptRequestId());
        if (attempt == null) throw new IllegalArgumentException("Payment attempt was not found for this order");
        if (transaction.getStatus() == PaymentStatus.SUCCESS) {
            if (!request.getRazorpayOrderId().equals(attempt.getGatewayOrderId())
                    || !request.getRazorpayPaymentId().equals(transaction.getGatewayPaymentId())
                    || !request.getRazorpayPaymentId().equals(attempt.getGatewayPaymentId())) {
                throw new IllegalArgumentException("This order has already been paid by a different payment attempt");
            }
            eventPublisher.paymentSucceeded(transaction.getOrderRequestId(), transaction.getGatewayPaymentId());
            return toResponse(transaction, attempt);
        }
        if (!request.getRazorpayOrderId().equals(attempt.getGatewayOrderId())
                || !request.getRazorpayOrderId().equals(transaction.getGatewayOrderId())) {
            throw new IllegalArgumentException("Gateway order does not match this checkout");
        }

        String expectedSignature = RazorpaySignatureVerifier.hmacSha256(gatewaySecret(),
                request.getRazorpayOrderId() + "|" + request.getRazorpayPaymentId());
        if (!RazorpaySignatureVerifier.matches(expectedSignature, request.getRazorpaySignature())) {
            throw new IllegalArgumentException("Payment signature is invalid");
        }
        JsonNode payment = gateway.fetchPayment(request.getRazorpayPaymentId());
        ensureGatewayPaymentMatches(transaction, payment);
        String status = text(payment, "status");
        if ("captured".equalsIgnoreCase(status)) return markSuccess(transaction, attempt, request.getRazorpayPaymentId(), true);
        if ("failed".equalsIgnoreCase(status)) {
            PaymentAttempt failed = store.markPaymentResult(transaction.getOrderRequestId(), attempt.getAttemptRequestId(), request.getRazorpayPaymentId(),
                    PaymentStatus.FAILED, "Razorpay reports that this payment failed. You may retry the same order.", true);
            return toResponse(store.find(transaction.getOrderRequestId()), failed);
        }
        PaymentAttempt pending = store.markPaymentResult(transaction.getOrderRequestId(), attempt.getAttemptRequestId(), request.getRazorpayPaymentId(),
                PaymentStatus.PENDING, "Verifying your payment. Please wait...", true);
        return toResponse(store.find(transaction.getOrderRequestId()), pending);
    }

    public PaymentResponse checkStatus(String customerEmail, String authorization, String orderRequestId) {
        OrderSnapshot order = findOwnedOrder(orderRequestId, authorization);
        PaymentTransaction transaction = store.find(orderRequestId);
        if (transaction == null) throw new IllegalArgumentException("Payment was not created for this order");
        assertPaymentOwnership(transaction, customerEmail, money(order.totalAmount));
        PaymentAttempt attempt = store.latestAttempt(orderRequestId);
        if (transaction.getStatus() == PaymentStatus.SUCCESS) {
            if (attempt != null && attempt.getGatewayOrderId().startsWith("NPCI-DEMO-ORDER-")) {
                eventPublisher.paymentSucceeded(transaction.getOrderRequestId(), transaction.getGatewayPaymentId(), transaction.getMessage());
            } else {
                eventPublisher.paymentSucceeded(transaction.getOrderRequestId(), transaction.getGatewayPaymentId());
            }
            return toResponse(transaction, attempt);
        }
        if (attempt == null) return toResponse(transaction, null);
        if (attempt.getGatewayOrderId() != null && attempt.getGatewayOrderId().startsWith("NPCI-DEMO-ORDER-")) {
            return toResponse(transaction, attempt);
        }
        if (attempt.getGatewayPaymentId() == null) return reconcileOrderPayments(transaction, attempt);
        JsonNode payment = gateway.fetchPayment(attempt.getGatewayPaymentId());
        ensureGatewayPaymentMatches(transaction, payment);
        String status = text(payment, "status");
        if ("captured".equalsIgnoreCase(status)) return markSuccess(transaction, attempt, attempt.getGatewayPaymentId(), true);
        if ("failed".equalsIgnoreCase(status)) {
            PaymentAttempt failed = store.markPaymentResult(orderRequestId, attempt.getAttemptRequestId(), attempt.getGatewayPaymentId(),
                    PaymentStatus.FAILED, "Razorpay reports that this payment failed. You may retry the same order.", true);
            return toResponse(store.find(orderRequestId), failed);
        }
        PaymentAttempt pending = store.markPaymentResult(orderRequestId, attempt.getAttemptRequestId(), attempt.getGatewayPaymentId(),
                PaymentStatus.PENDING, "We couldn't verify your payment yet. Your payment status is being checked.", true);
        return toResponse(store.find(orderRequestId), pending);
    }

    private PaymentResponse reconcileOrderPayments(PaymentTransaction transaction, PaymentAttempt attempt) {
        JsonNode response = gateway.fetchOrderPayments(transaction.getGatewayOrderId());
        JsonNode payments = response == null ? null : response.path("items");
        if (payments == null || !payments.isArray() || payments.isEmpty()) {
            PaymentAttempt cancelled = store.markPaymentResult(transaction.getOrderRequestId(), attempt.getAttemptRequestId(), null,
                    PaymentStatus.CANCELLED, "Razorpay confirmed that no payment was completed. You can retry this order.", true);
            return toResponse(store.find(transaction.getOrderRequestId()), cancelled);
        }

        JsonNode failedPayment = null;
        JsonNode pendingPayment = null;
        for (JsonNode item : payments) {
            ensureGatewayPaymentMatches(transaction, item);
            String status = text(item, "status");
            String paymentId = text(item, "id");
            if ("captured".equalsIgnoreCase(status)) {
                PaymentAttempt ownerAttempt = paymentId == null ? null : store.findAttemptByGatewayPayment(paymentId);
                return markSuccess(transaction, ownerAttempt == null ? attempt : ownerAttempt, paymentId, true);
            }
            if ("failed".equalsIgnoreCase(status)) failedPayment = item;
            else pendingPayment = item;
        }
        if (failedPayment != null && pendingPayment == null) {
            String paymentId = text(failedPayment, "id");
            PaymentAttempt failed = store.markPaymentResult(transaction.getOrderRequestId(), attempt.getAttemptRequestId(), paymentId,
                    PaymentStatus.FAILED, "Razorpay confirmed that the payment failed. You may retry this order.", true);
            return toResponse(store.find(transaction.getOrderRequestId()), failed);
        }
        String paymentId = text(pendingPayment, "id");
        PaymentAttempt pending = store.markPaymentResult(transaction.getOrderRequestId(), attempt.getAttemptRequestId(), paymentId,
                PaymentStatus.PENDING, "We couldn't verify your payment yet. Your payment status is being checked.", true);
        return toResponse(store.find(transaction.getOrderRequestId()), pending);
    }

    @Transactional
    public WebhookResult processWebhook(String eventId, String signature, byte[] rawBody) {
        if (!gateway.isEnabled() || gateway.getWebhookSecret().isBlank()) {
            throw new PaymentSetupException("Razorpay webhook verification is not configured");
        }
        String body = new String(rawBody, StandardCharsets.UTF_8);
        String expected = RazorpaySignatureVerifier.hmacSha256(gateway.getWebhookSecret(), body);
        if (!RazorpaySignatureVerifier.matches(expected, signature)) throw new IllegalArgumentException("Webhook signature is invalid");
        String safeEventId = (eventId == null || eventId.isBlank()) ? sha256(body) : eventId;
        if (webhookRepository.existsByEventId(safeEventId)) return new WebhookResult(true, "Duplicate event already processed");

        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String eventType = text(root, "event");
            JsonNode payment = root.path("payload").path("payment").path("entity");
            String gatewayOrderId = text(payment, "order_id");
            String gatewayPaymentId = text(payment, "id");
            if (gatewayOrderId != null && ("payment.captured".equals(eventType)
                    || "payment.failed".equals(eventType) || "order.paid".equals(eventType))) {
                PaymentTransaction transaction = store.findByGatewayOrder(gatewayOrderId);
                if (transaction != null) {
                    PaymentAttempt attempt = gatewayPaymentId == null ? store.latestAttempt(transaction.getOrderRequestId())
                            : store.findAttemptByGatewayPayment(gatewayPaymentId);
                    if (attempt == null) attempt = store.latestAttempt(transaction.getOrderRequestId());
                    if (attempt == null || !attempt.getGatewayOrderId().equals(gatewayOrderId)) {
                        throw new IllegalArgumentException("Webhook does not match a recorded payment attempt");
                    }
                    JsonNode authoritative = gatewayPaymentId == null ? payment : gateway.fetchPayment(gatewayPaymentId);
                    ensureGatewayPaymentMatches(transaction, authoritative);
                    String status = text(authoritative, "status");
                    if ("captured".equalsIgnoreCase(status)) markSuccess(transaction, attempt, gatewayPaymentId, true);
                    else if ("failed".equalsIgnoreCase(status)) {
                        store.markPaymentResult(transaction.getOrderRequestId(), attempt.getAttemptRequestId(), gatewayPaymentId, PaymentStatus.FAILED,
                                "Razorpay reports that this payment failed. You may retry the same order.", true);
                    }
                }
            }
            GatewayWebhookEvent savedEvent = new GatewayWebhookEvent();
            savedEvent.setEventId(safeEventId);
            savedEvent.setEventType(eventType == null ? "unknown" : eventType);
            webhookRepository.saveAndFlush(savedEvent);
            return new WebhookResult(false, "Webhook accepted");
        } catch (DataIntegrityViolationException duplicate) {
            return new WebhookResult(true, "Duplicate event already processed");
        } catch (PaymentSetupException | IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Webhook could not be processed; Razorpay can safely retry it.", ex);
        }
    }

    private PaymentResponse markSuccess(PaymentTransaction transaction, PaymentAttempt attempt, String paymentId, boolean verified) {
        PaymentAttempt updatedAttempt = store.markPaymentResult(transaction.getOrderRequestId(), attempt.getAttemptRequestId(), paymentId,
                PaymentStatus.SUCCESS, "Payment captured and verified by Razorpay.", verified);
        PaymentTransaction updated = store.find(transaction.getOrderRequestId());
        eventPublisher.paymentSucceeded(transaction.getOrderRequestId(), paymentId);
        return toResponse(updated, updatedAttempt);
    }

    private void ensureGatewayPaymentMatches(PaymentTransaction transaction, JsonNode payment) {
        if (payment == null || payment.isMissingNode()) throw new IllegalArgumentException("Gateway payment was not found");
        if (!transaction.getGatewayOrderId().equals(text(payment, "order_id"))) {
            throw new IllegalArgumentException("Gateway payment belongs to another order");
        }
        long expectedPaise = transaction.getAmount().movePointRight(2).longValueExact();
        if (payment.path("amount").asLong(-1) != expectedPaise || !"INR".equalsIgnoreCase(text(payment, "currency"))) {
            throw new IllegalArgumentException("Gateway amount or currency does not match the order");
        }
    }

    private OrderSnapshot findOwnedOrder(String orderRequestId, String authorization) {
        if (authorization == null || authorization.isBlank()) throw new IllegalArgumentException("Sign in before paying");
        try {
            OrderSnapshot order = orderClient.get().uri("/api/orders/me/reference/{id}", orderRequestId)
                    .header("Authorization", authorization).retrieve().body(OrderSnapshot.class);
            if (order == null || order.id == null || !orderRequestId.equals(order.orderRequestId)) {
                throw new IllegalArgumentException("Order not found");
            }
            return order;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Could not verify your order with the order service", ex);
        }
    }

    private void assertPaymentOwnership(PaymentTransaction payment, String email, BigDecimal trustedAmount) {
        if (email == null || !payment.getCustomerEmail().equalsIgnoreCase(email)) {
            throw new IllegalArgumentException("Payment not found");
        }
        if (payment.getAmount().compareTo(trustedAmount) != 0) {
            throw new IllegalStateException("Order amount changed after payment initialization. Contact support before retrying.");
        }
    }

    private String gatewaySecret() {
        // The secret is used only for callback verification and is never returned to the browser.
        return gateway.getKeySecretForSignatureVerification();
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("Invalid order amount");
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not hash webhook body", ex);
        }
    }

    private PaymentAttempt createAttemptSafely(String orderRequestId, String attemptRequestId, String gatewayOrderId, BigDecimal amount) {
        try {
            return store.createAttempt(orderRequestId, attemptRequestId, gatewayOrderId, amount);
        } catch (DataIntegrityViolationException duplicateAttempt) {
            PaymentAttempt existing = store.findAttempt(orderRequestId, attemptRequestId);
            if (existing == null) throw duplicateAttempt;
            return existing;
        }
    }

    private PaymentResponse checkoutResponse(PaymentTransaction transaction, PaymentAttempt attempt) {
        PaymentResponse response = toResponse(transaction, attempt);
        if (attempt.getStatus() != PaymentStatus.FAILED && attempt.getStatus() != PaymentStatus.CANCELLED) {
            response.setKeyId(gateway.getPublicKeyId());
        }
        return response;
    }

    private PaymentResponse toResponse(PaymentTransaction transaction, PaymentAttempt attempt) {
        PaymentResponse response = new PaymentResponse();
        boolean paid = transaction.getStatus() == PaymentStatus.SUCCESS;
        response.setOrderRequestId(transaction.getOrderRequestId());
        response.setAttemptRequestId(attempt == null ? null : attempt.getAttemptRequestId());
        response.setPaymentMethod(transaction.getPaymentMethod());
        response.setAmount(transaction.getAmount());
        response.setCurrency(transaction.getCurrency());
        response.setStatus((paid ? PaymentStatus.SUCCESS : attempt == null ? transaction.getStatus() : attempt.getStatus()).name());
        response.setProviderReference(paid || attempt == null || attempt.getGatewayPaymentId() == null
                ? transaction.getProviderReference() : attempt.getGatewayPaymentId());
        response.setGatewayOrderId(attempt == null ? transaction.getGatewayOrderId() : attempt.getGatewayOrderId());
        response.setGatewayPaymentId(paid || attempt == null ? transaction.getGatewayPaymentId() : attempt.getGatewayPaymentId());
        response.setMessage(paid || attempt == null ? transaction.getMessage() : attempt.getMessage());
        response.setVerificationStatus(paid || attempt == null ? transaction.getVerificationStatus() : attempt.getVerificationStatus());
        response.setAttemptCount(transaction.getAttemptCount());
        response.setPaidAt(transaction.getPaidAt());
        return response;
    }

    @Deprecated
    public PaymentResponse process(com.bookstore.payment.dto.PaymentRequest request) {
        throw new IllegalStateException("Direct payment processing is disabled. Use the verified Razorpay checkout flow.");
    }

    public PaymentResponse refund(com.bookstore.payment.dto.RefundRequest request) {
        throw new UnsupportedOperationException("Refunds require a separately configured Razorpay refund workflow.");
    }

    public record WebhookResult(boolean duplicate, String message) {}

    public static class PaymentSetupException extends RuntimeException {
        public PaymentSetupException(String message) { super(message); }
    }

    public static class PaymentOutcomeUnknownException extends RuntimeException {
        public PaymentOutcomeUnknownException(String message, Throwable cause) { super(message, cause); }
    }

    private static final class OrderSnapshot {
        public Long id;
        public String orderRequestId;
        public String customerEmail;
        public String paymentMethod;
        public String status;
        public BigDecimal totalAmount;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getOrderRequestId() { return orderRequestId; }
        public void setOrderRequestId(String orderRequestId) { this.orderRequestId = orderRequestId; }
        public String getCustomerEmail() { return customerEmail; }
        public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }
        public String getPaymentMethod() { return paymentMethod; }
        public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public BigDecimal getTotalAmount() { return totalAmount; }
        public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    }
}
