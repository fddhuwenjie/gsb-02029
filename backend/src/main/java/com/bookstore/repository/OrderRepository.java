package com.bookstore.repository;

import com.bookstore.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Page<Order> findByUserId(Long userId, Pageable pageable);
    Page<Order> findByStatus(String status, Pageable pageable);

    Optional<Order> findByOrderNo(String orderNo);

    Optional<Order> findByPaymentNo(String paymentNo);

    @Query("SELECT o FROM Order o WHERE o.status IN ('PENDING', 'PAYING') AND o.expireTime < :now")
    List<Order> findExpiredOrders(@Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE Order o SET o.status = :toStatus, o.updatedAt = :now WHERE o.id = :id AND o.status = :fromStatus")
    int compareAndSetStatus(@Param("id") Long id,
                            @Param("fromStatus") String fromStatus,
                            @Param("toStatus") String toStatus,
                            @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE Order o SET o.status = :toStatus, o.updatedAt = :now, o.paymentNo = :paymentNo, o.paymentMethod = :paymentMethod, o.payTime = :now WHERE o.id = :id AND o.status = :fromStatus")
    int markAsPaid(@Param("id") Long id,
                   @Param("fromStatus") String fromStatus,
                   @Param("toStatus") String toStatus,
                   @Param("paymentNo") String paymentNo,
                   @Param("paymentMethod") String paymentMethod,
                   @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE Order o SET o.status = :toStatus, o.updatedAt = :now, o.trackingNo = :trackingNo WHERE o.id = :id AND o.status = :fromStatus")
    int markAsShipped(@Param("id") Long id,
                      @Param("fromStatus") String fromStatus,
                      @Param("toStatus") String toStatus,
                      @Param("trackingNo") String trackingNo,
                      @Param("now") LocalDateTime now);

    @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE o.status = 'COMPLETED'")
    BigDecimal getTotalSales();
    
    @Query("SELECT COUNT(o) FROM Order o")
    Long countAllOrders();
}
