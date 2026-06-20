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

    @Query("SELECT o FROM Order o JOIN OrderItem oi ON o.id = oi.orderId JOIN Book b ON oi.bookId = b.id WHERE b.sellerId = :sellerId")
    Page<Order> findBySellerId(@Param("sellerId") Long sellerId, Pageable pageable);

    @Query("SELECT o FROM Order o JOIN OrderItem oi ON o.id = oi.orderId JOIN Book b ON oi.bookId = b.id WHERE b.sellerId = :sellerId AND o.status = :status")
    Page<Order> findBySellerIdAndStatus(@Param("sellerId") Long sellerId, @Param("status") String status, Pageable pageable);
    
    @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE o.status = 'COMPLETED'")
    BigDecimal getTotalSales();
    
    @Query("SELECT COUNT(o) FROM Order o")
    Long countAllOrders();

    @Modifying
    @Query("UPDATE Order o SET o.status = :newStatus, o.payTime = :payTime, o.paymentNo = :paymentNo, o.payMethod = :payMethod, o.updatedAt = :updatedAt " +
           "WHERE o.id = :orderId AND o.status = :expectedStatus")
    int markAsPaid(@Param("orderId") Long orderId,
                   @Param("expectedStatus") String expectedStatus,
                   @Param("newStatus") String newStatus,
                   @Param("payTime") LocalDateTime payTime,
                   @Param("paymentNo") String paymentNo,
                   @Param("payMethod") String payMethod,
                   @Param("updatedAt") LocalDateTime updatedAt);

    @Modifying
    @Query("UPDATE Order o SET o.status = :newStatus, o.updatedAt = :updatedAt, o.cancelTime = :cancelTime " +
           "WHERE o.id = :orderId AND o.status = :expectedStatus")
    int cancelOrder(@Param("orderId") Long orderId,
                    @Param("expectedStatus") String expectedStatus,
                    @Param("newStatus") String newStatus,
                    @Param("updatedAt") LocalDateTime updatedAt,
                    @Param("cancelTime") LocalDateTime cancelTime);

    @Modifying
    @Query("UPDATE Order o SET o.status = :newStatus, o.updatedAt = :updatedAt, o.shipTime = :shipTime, o.trackingNo = :trackingNo " +
           "WHERE o.id = :orderId AND o.status = :expectedStatus")
    int markAsShipped(@Param("orderId") Long orderId,
                      @Param("expectedStatus") String expectedStatus,
                      @Param("newStatus") String newStatus,
                      @Param("updatedAt") LocalDateTime updatedAt,
                      @Param("shipTime") LocalDateTime shipTime,
                      @Param("trackingNo") String trackingNo);

    @Modifying
    @Query("UPDATE Order o SET o.status = :newStatus, o.updatedAt = :updatedAt, o.completeTime = :completeTime " +
           "WHERE o.id = :orderId AND o.status = :expectedStatus")
    int markAsCompleted(@Param("orderId") Long orderId,
                        @Param("expectedStatus") String expectedStatus,
                        @Param("newStatus") String newStatus,
                        @Param("updatedAt") LocalDateTime updatedAt,
                        @Param("completeTime") LocalDateTime completeTime);

    List<Order> findByStatusAndExpireTimeBefore(String status, LocalDateTime dateTime);
}
