package com.bookstore.buyback.repository;

import com.bookstore.buyback.domain.BuybackPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BuybackPaymentRepository extends JpaRepository<BuybackPayment, String> {
    Optional<BuybackPayment> findByBuybackId(String buybackId);
}
