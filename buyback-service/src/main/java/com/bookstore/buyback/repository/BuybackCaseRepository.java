package com.bookstore.buyback.repository;

import com.bookstore.buyback.domain.BuybackCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface BuybackCaseRepository extends JpaRepository<BuybackCase, String> {
    List<BuybackCase> findAllByCustomerEmailOrderByCreatedAtDesc(String customerEmail);
    List<BuybackCase> findAllByOrderByCreatedAtDesc();
    Optional<BuybackCase> findByIdAndCustomerEmail(String id, String customerEmail);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from BuybackCase c where c.id = :id")
    Optional<BuybackCase> findByIdForUpdate(@Param("id") String id);
}
