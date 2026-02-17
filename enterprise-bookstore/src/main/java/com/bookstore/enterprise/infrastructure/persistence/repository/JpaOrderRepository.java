package com.bookstore.enterprise.infrastructure.persistence.repository;

import com.bookstore.enterprise.infrastructure.persistence.entity.JpaOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JpaOrderRepository extends JpaRepository<JpaOrderEntity, Long> {

    List<JpaOrderEntity> findByUser_IdOrderByCreatedAtDesc(Long userId);

    List<JpaOrderEntity> findAllByOrderByCreatedAtDesc();
}
