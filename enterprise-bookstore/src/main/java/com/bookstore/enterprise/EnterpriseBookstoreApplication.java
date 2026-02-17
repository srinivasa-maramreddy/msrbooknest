package com.bookstore.enterprise;

import com.bookstore.enterprise.infrastructure.config.AdminSecurityProperties;
import com.bookstore.enterprise.infrastructure.config.JwtProperties;
import com.bookstore.enterprise.infrastructure.config.OtpProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({JwtProperties.class, AdminSecurityProperties.class, OtpProperties.class})
public class EnterpriseBookstoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(EnterpriseBookstoreApplication.class, args);
    }
}
