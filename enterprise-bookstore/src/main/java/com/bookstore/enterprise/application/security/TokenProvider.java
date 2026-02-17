package com.bookstore.enterprise.application.security;

import com.bookstore.enterprise.domain.model.AppUser;

public interface TokenProvider {

    String generateToken(AppUser user);

    String extractUsername(String token);

    boolean isTokenValid(String token, String username);
}
