package com.bookstore.enterprise.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public class AdminSecurityProperties {

    private String adminRegistrationKey;

    public String getAdminRegistrationKey() {
        return adminRegistrationKey;
    }

    public void setAdminRegistrationKey(String adminRegistrationKey) {
        this.adminRegistrationKey = adminRegistrationKey;
    }
}
