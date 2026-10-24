package com.bookstore.buyback.messaging;

import com.bookstore.buyback.domain.BuybackCase;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
public class BuybackEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(BuybackEventPublisher.class);
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public BuybackEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper,
                                 @Value("${app.kafka.buyback-events-enabled:true}") boolean enabled) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    public void statusChanged(BuybackCase buyback, String action) {
        if (!enabled) return;
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "buybackId", buyback.getId(),
                    "customerEmail", buyback.getCustomerEmail(),
                    "status", buyback.getStatus().name(),
                    "action", action,
                    "occurredAt", LocalDateTime.now().toString()));
            kafkaTemplate.send("buyback.status-events", buyback.getId(), payload)
                    .whenComplete((result, error) -> {
                        if (error != null) log.warn("Could not publish mock buyback event for {}", buyback.getId());
                    });
        } catch (JsonProcessingException error) {
            log.warn("Could not serialize mock buyback event for {}", buyback.getId());
        }
    }
}
