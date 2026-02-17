package com.bookstore.enterprise.infrastructure.persistence.repository;

import com.bookstore.enterprise.infrastructure.persistence.entity.JpaBookEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaBookRepository extends JpaRepository<JpaBookEntity, Long> {
}
