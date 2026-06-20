package com.bookstore.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "payment_logs")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class PaymentLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "order_no", nullable = false)
    private String orderNo;

    @Column(name = "payment_no", unique = true, nullable = false)
    private String paymentNo;

    @Column(name = "third_party_no")
    private String thirdPartyNo;

    @Column(nullable = false)
    private String method;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String rawResponse;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
