package com.bookstore.catalog.repository;

import com.bookstore.catalog.entity.InventoryReservation;
import com.bookstore.catalog.entity.InventoryReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {
    Optional<InventoryReservation> findByOrderRequestId(String orderRequestId);
    List<InventoryReservation> findTop100ByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
            InventoryReservationStatus status, LocalDateTime now);
}
