package com.bookstore.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Component
public class RazorpayGatewayClient {
    private final RestClient client;
    private final String keyId;
    private final String keySecret;
    private final String webhookSecret;
    private final boolean enabled;
    private final String mode;

    public RazorpayGatewayClient(RestClient.Builder builder,
                                 @Value("${razorpay.base-url:https://api.razorpay.com/v1}") String baseUrl,
                                 @Value("${razorpay.key-id:}") String keyId,
                                 @Value("${razorpay.key-secret:}") String keySecret,
                                 @Value("${razorpay.webhook-secret:}") String webhookSecret,
                                 @Value("${razorpay.enabled:false}") boolean enabled,
                                 @Value("${razorpay.mode:test}") String mode,
                                 @Value("${razorpay.allow-live:false}") boolean allowLive) {
        this.keyId = keyId;
        this.keySecret = keySecret;
        this.webhookSecret = webhookSecret;
        this.mode = mode.toLowerCase();
        boolean testCredentials = keyId.startsWith("rzp_test_") && !keySecret.isBlank();
        boolean liveCredentials = keyId.startsWith("rzp_live_") && !keySecret.isBlank() && allowLive;
        this.enabled = enabled && (("test".equals(this.mode) && testCredentials)
                || ("live".equals(this.mode) && liveCredentials));
        String credentials = Base64.getEncoder().encodeToString((keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));
        this.client = builder.baseUrl(baseUrl).defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json").build();
    }

    public boolean isEnabled() { return enabled; }
    public String getPublicKeyId() { return keyId; }
    public String getWebhookSecret() { return webhookSecret; }
    String getKeySecretForSignatureVerification() { return keySecret; }
    public String getMode() { return mode; }

    public JsonNode createOrder(BigDecimal amount, String receipt, String internalOrderId) {
        if (!enabled) throw new IllegalStateException("Razorpay test checkout is not configured. Set the server-side Razorpay test credentials.");
        long paise = amount.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
        Map<String, Object> payload = Map.of(
                "amount", paise,
                "currency", "INR",
                "receipt", receipt.length() > 40 ? receipt.substring(0, 40) : receipt,
                "partial_payment", false,
                "notes", Map.of("msr_order_id", internalOrderId));
        return client.post().uri("/orders").body(payload).retrieve().body(JsonNode.class);
    }

    public JsonNode fetchPayment(String paymentId) {
        if (!enabled) throw new IllegalStateException("Razorpay test checkout is not configured.");
        return client.get().uri("/payments/{paymentId}", paymentId).retrieve().body(JsonNode.class);
    }

    public JsonNode fetchOrderPayments(String gatewayOrderId) {
        if (!enabled) throw new IllegalStateException("Razorpay test checkout is not configured.");
        return client.get().uri("/orders/{orderId}/payments", gatewayOrderId).retrieve().body(JsonNode.class);
    }
}
