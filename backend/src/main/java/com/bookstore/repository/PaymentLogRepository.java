package com.bookstore.repository;

import com.bookstore.entity.PaymentLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PaymentLogRepository extends JpaRepository<PaymentLog, Long> {
    Optional<PaymentLog> findByPaymentNo(String paymentNo);
    boolean existsByPaymentNo(String paymentNo);
    PaymentLog findByOrderIdAndStatus(Long orderId, String status);
}
