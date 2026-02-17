package com.bookstore.enterprise.infrastructure.persistence.repository;

import com.bookstore.enterprise.infrastructure.persistence.entity.JpaAuthorEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaAuthorRepository extends JpaRepository<JpaAuthorEntity, Long> {
}
