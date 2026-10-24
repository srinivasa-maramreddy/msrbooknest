package com.bookstore.buyback.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(name = "app.kafka.buyback-events-enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConfig {
    @Bean
    NewTopic buybackStatusTopic() {
        return TopicBuilder.name("buyback.status-events").partitions(1).replicas(1).build();
    }
}
