package com.bookstore.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Entity
@Table(name = "orders")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "user"})
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "order_no", unique = true, nullable = false)
    private String orderNo;
    
    @Column(name = "user_id", nullable = false)
    private Long userId;
    
    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;
    
    private String status = "PENDING";
    private String address;
    private String phone;
    private String receiver;
    
    @Column(columnDefinition = "TEXT")
    private String remark;

    @Column(name = "tracking_no")
    private String trackingNo;

    /**
     * 支付流水号（来自支付网关或 mock 生成），全局唯一，用于幂等。
     * NULL 表示尚未支付。
     */
    @Column(name = "payment_no", unique = true)
    private String paymentNo;

    /**
     * 支付渠道：WECHAT / ALIPAY / MOCK 等。
     */
    @Column(name = "payment_method")
    private String paymentMethod;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    /**
     * 乐观锁版本号，避免管理端并发修改订单时丢失更新。
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    @OneToMany(fetch = FetchType.EAGER)
    @JoinColumn(name = "order_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private List<OrderItem> items;
}
