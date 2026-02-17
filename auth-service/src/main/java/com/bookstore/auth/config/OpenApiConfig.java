package com.bookstore.auth.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI authServiceOpenAPI() {
        return new OpenAPI().info(
                new Info()
                        .title("Bookstore Auth Service API")
                        .description("Authentication APIs for customer and admin login/registration.")
                        .version("v1")
                        .contact(new Contact().name("Bookstore Team"))
        );
    }
}
