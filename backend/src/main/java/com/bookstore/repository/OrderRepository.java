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

public interface OrderRepository extends JpaRepository<Order, Long> {
    Page<Order> findByUserId(Long userId, Pageable pageable);
    Page<Order> findByStatus(String status, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.id IN (" +
           "SELECT DISTINCT oi.orderId FROM OrderItem oi JOIN Book b ON oi.bookId = b.id WHERE b.sellerId = :sellerId) " +
           "ORDER BY o.createdAt DESC")
    Page<Order> findBySellerId(@Param("sellerId") Long sellerId, Pageable pageable);

    @Query("SELECT DISTINCT b.sellerId FROM OrderItem oi JOIN Book b ON oi.bookId = b.id WHERE oi.orderId = :orderId")
    List<Long> findSellerIdsByOrderId(@Param("orderId") Long orderId);

    @Modifying
    @Query("UPDATE Order o SET o.status = :newStatus, o.updatedAt = :now WHERE o.id = :id AND o.status = :oldStatus")
    int casStatus(@Param("id") Long id,
                  @Param("oldStatus") String oldStatus,
                  @Param("newStatus") String newStatus,
                  @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE Order o SET o.status = 'PAID', o.paymentId = :paymentId, o.paymentMethod = :paymentMethod, " +
           "o.paidAt = :now, o.updatedAt = :now WHERE o.id = :id AND o.status = 'PENDING'")
    int casMarkPaid(@Param("id") Long id,
                    @Param("paymentId") String paymentId,
                    @Param("paymentMethod") String paymentMethod,
                    @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE Order o SET o.status = 'CANCELLED', o.cancelledAt = :now, o.updatedAt = :now, o.cancelReason = :reason " +
           "WHERE o.id = :id AND o.status IN ('PENDING', 'PAID')")
    int casCancel(@Param("id") Long id,
                  @Param("now") LocalDateTime now,
                  @Param("reason") String reason);

    @Modifying
    @Query("UPDATE Order o SET o.status = 'SHIPPED', o.trackingNo = :trackingNo, o.shippedAt = :now, o.updatedAt = :now " +
           "WHERE o.id = :id AND o.status = 'PAID'")
    int casShip(@Param("id") Long id,
                @Param("trackingNo") String trackingNo,
                @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE Order o SET o.status = 'COMPLETED', o.completedAt = :now, o.updatedAt = :now " +
           "WHERE o.id = :id AND o.status = 'SHIPPED'")
    int casComplete(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Query("SELECT o FROM Order o WHERE o.status = 'PENDING' AND o.expireAt < :now")
    List<Order> findExpiredOrders(@Param("now") LocalDateTime now);

    @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE o.status = 'COMPLETED'")
    BigDecimal getTotalSales();

    @Query("SELECT COUNT(o) FROM Order o")
    Long countAllOrders();
}
