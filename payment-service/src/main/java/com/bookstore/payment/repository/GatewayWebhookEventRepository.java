package com.bookstore.payment.repository;

import com.bookstore.payment.entity.GatewayWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GatewayWebhookEventRepository extends JpaRepository<GatewayWebhookEvent, Long> {
    boolean existsByEventId(String eventId);
}
