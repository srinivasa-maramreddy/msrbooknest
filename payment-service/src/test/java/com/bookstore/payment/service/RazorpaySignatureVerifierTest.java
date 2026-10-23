package com.bookstore.payment.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RazorpaySignatureVerifierTest {
    @Test
    void verifiesExpectedHmacAndRejectsChangedSignature() {
        String signature = RazorpaySignatureVerifier.hmacSha256("test-secret", "order_123|pay_456");

        assertEquals(64, signature.length());
        assertTrue(RazorpaySignatureVerifier.matches(signature, signature));
        assertFalse(RazorpaySignatureVerifier.matches(signature, signature.substring(0, 63) + "0"));
        assertFalse(RazorpaySignatureVerifier.matches(signature, null));
    }

    @Test
    void verifiesWebhookOverExactRawRequestBody() {
        String rawBody = "{\"event\":\"payment.captured\",\"payload\":{}}";
        String signature = RazorpaySignatureVerifier.hmacSha256("webhook-secret", rawBody);

        assertTrue(RazorpaySignatureVerifier.matches(
                RazorpaySignatureVerifier.hmacSha256("webhook-secret", rawBody), signature));
        assertFalse(RazorpaySignatureVerifier.matches(
                RazorpaySignatureVerifier.hmacSha256("webhook-secret", rawBody + " "), signature));
    }
}
