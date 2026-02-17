package com.bookstore.enterprise.infrastructure.persistence.repository;

import com.bookstore.enterprise.infrastructure.persistence.entity.JpaUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JpaUserRepository extends JpaRepository<JpaUserEntity, Long> {

    Optional<JpaUserEntity> findByEmail(String email);

    Optional<JpaUserEntity> findByPhoneNumber(String phoneNumber);

    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);
}
