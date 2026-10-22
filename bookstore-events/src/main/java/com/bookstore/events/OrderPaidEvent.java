package com.bookstore.events;

import java.time.Instant;

public record OrderPaidEvent(
        String eventId,
        int schemaVersion,
        Instant occurredAt,
        String orderRequestId,
        String paymentReference
) {
}
