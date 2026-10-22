package com.bookstore.events;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderPaymentRequestedEvent(
        String eventId,
        int schemaVersion,
        Instant occurredAt,
        String orderRequestId,
        String customerEmail,
        BigDecimal amount,
        String paymentMethod,
        String paymentAddress
) {
}
