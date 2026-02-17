package com.bookstore.enterprise.infrastructure.persistence.repository;

import com.bookstore.enterprise.infrastructure.persistence.entity.JpaCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaCategoryRepository extends JpaRepository<JpaCategoryEntity, Long> {
}
