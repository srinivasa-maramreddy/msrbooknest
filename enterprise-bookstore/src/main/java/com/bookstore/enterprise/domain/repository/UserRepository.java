package com.bookstore.enterprise.domain.repository;

import com.bookstore.enterprise.domain.model.AppUser;

import java.util.Optional;

public interface UserRepository {

    Optional<AppUser> findById(Long id);

    Optional<AppUser> findByEmail(String email);

    Optional<AppUser> findByPhoneNumber(String phoneNumber);

    AppUser save(AppUser user);

    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);
}
