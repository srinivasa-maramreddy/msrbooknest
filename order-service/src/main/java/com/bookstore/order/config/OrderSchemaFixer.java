package com.bookstore.order.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class OrderSchemaFixer {

    @Bean
    ApplicationRunner orderStatusColumnFixer(JdbcTemplate jdbcTemplate) {
        return args -> {
            try {
                jdbcTemplate.execute("ALTER TABLE orders MODIFY COLUMN status VARCHAR(40) NOT NULL");
            } catch (Exception ignored) {
                // Ignore if the column is already compatible on a fresh database.
            }
        };
    }
}
