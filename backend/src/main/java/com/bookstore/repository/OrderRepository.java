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

    /**
     * 拉取所有处于指定状态、且 createdAt 早于阈值的订单，用于超时取消扫描。
     */
    List<Order> findByStatusAndCreatedAtBefore(String status, LocalDateTime threshold);

    @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE o.status = 'COMPLETED'")
    BigDecimal getTotalSales();
    
    @Query("SELECT COUNT(o) FROM Order o")
    Long countAllOrders();

    /**
     * CAS 标记订单为已支付：仅当当前状态为 PENDING 且 paymentNo 仍为空时才会成功。
     * 通过唯一的 paymentNo 与 status 双条件，使支付确认 / 网关回调天然幂等。
     * 返回受影响行数：1 = 首次成功；0 = 已支付或状态不匹配。
     */
    @Modifying
    @Query("UPDATE Order o SET o.status = 'PAID', o.paymentNo = :paymentNo, " +
           "o.paymentMethod = :paymentMethod, o.paidAt = :paidAt, o.updatedAt = :paidAt " +
           "WHERE o.id = :id AND o.status = 'PENDING' AND o.paymentNo IS NULL")
    int markPaidIfPending(@Param("id") Long id,
                          @Param("paymentNo") String paymentNo,
                          @Param("paymentMethod") String paymentMethod,
                          @Param("paidAt") LocalDateTime paidAt);

    /**
     * CAS 取消订单：仅当当前状态在允许集合内时才会成功。
     */
    @Modifying
    @Query("UPDATE Order o SET o.status = 'CANCELLED', o.updatedAt = :updatedAt " +
           "WHERE o.id = :id AND o.status IN :allowedFromStatuses")
    int cancelIfStatusIn(@Param("id") Long id,
                         @Param("allowedFromStatuses") List<String> allowedFromStatuses,
                         @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * CAS 状态迁移：仅当当前状态等于 fromStatus 时才会写入 toStatus。
     * 用于 PAID -> SHIPPED -> COMPLETED 的转移，避免管理端并发越权。
     */
    @Modifying
    @Query("UPDATE Order o SET o.status = :toStatus, o.updatedAt = :updatedAt, " +
           "o.trackingNo = COALESCE(:trackingNo, o.trackingNo) " +
           "WHERE o.id = :id AND o.status = :fromStatus")
    int transitionStatus(@Param("id") Long id,
                         @Param("fromStatus") String fromStatus,
                         @Param("toStatus") String toStatus,
                         @Param("trackingNo") String trackingNo,
                         @Param("updatedAt") LocalDateTime updatedAt);
}
