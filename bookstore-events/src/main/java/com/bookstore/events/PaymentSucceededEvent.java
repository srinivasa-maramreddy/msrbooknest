package com.bookstore.events;

import java.time.Instant;

public record PaymentSucceededEvent(
        String eventId,
        int schemaVersion,
        Instant occurredAt,
        String orderRequestId,
        String providerReference,
        String message
) {
}
