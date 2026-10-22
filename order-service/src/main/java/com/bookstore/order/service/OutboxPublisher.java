package com.bookstore.order.service;

import com.bookstore.order.entity.OutboxEvent;
import com.bookstore.order.repository.OutboxEventRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxPublisher {
    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(OutboxEventRepository outboxRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-ms:1000}")
    @Transactional
    public void publishPending() {
        for (OutboxEvent event : outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAscIdAsc()) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getEventKey(), event.getPayload()).get(10, TimeUnit.SECONDS);
                event.setPublishedAt(LocalDateTime.now());
                event.setLastError(null);
            } catch (Exception ex) {
                event.setAttempts(event.getAttempts() + 1);
                String message = ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage();
                event.setLastError(message == null ? "Kafka publish failed" : message.substring(0, Math.min(1000, message.length())));
                break;
            }
        }
    }
}
