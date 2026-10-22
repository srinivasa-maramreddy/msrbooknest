package com.bookstore.payment.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI paymentServiceOpenAPI() {
        return new OpenAPI().info(
                new Info()
                        .title("Bookstore Payment Service API")
                        .description("Mock payment APIs with idempotency support for checkout.")
                        .version("v1")
                        .contact(new Contact().name("Bookstore Team"))
        );
    }
}
