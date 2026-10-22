package com.bookstore.events;

import java.time.Instant;

public record OrderCancelledEvent(
        String eventId,
        int schemaVersion,
        Instant occurredAt,
        String orderRequestId,
        String reason
) {
}
