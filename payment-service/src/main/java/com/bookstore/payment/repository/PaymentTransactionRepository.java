package com.bookstore.payment.repository;

import com.bookstore.payment.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    Optional<PaymentTransaction> findByOrderRequestId(String orderRequestId);
    Optional<PaymentTransaction> findByGatewayOrderId(String gatewayOrderId);
    Optional<PaymentTransaction> findByGatewayPaymentId(String gatewayPaymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentTransaction p where p.orderRequestId = :orderRequestId")
    Optional<PaymentTransaction> findByOrderRequestIdForUpdate(@Param("orderRequestId") String orderRequestId);
}
