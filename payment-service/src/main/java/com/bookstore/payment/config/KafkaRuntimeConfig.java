package com.bookstore.payment.config;

import com.bookstore.events.BookstoreTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@EnableKafka
public class KafkaRuntimeConfig {
    @Bean
    NewTopic paymentRequestedTopic() {
        return TopicBuilder.name(BookstoreTopics.PAYMENT_REQUESTED).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic paymentSucceededTopic() {
        return TopicBuilder.name(BookstoreTopics.PAYMENT_SUCCEEDED).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic paymentFailedTopic() {
        return TopicBuilder.name(BookstoreTopics.PAYMENT_FAILED).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic orderPaidTopic() {
        return TopicBuilder.name(BookstoreTopics.ORDER_PAID).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic orderCancelledTopic() {
        return TopicBuilder.name(BookstoreTopics.ORDER_CANCELLED).partitions(3).replicas(1).build();
    }
}
