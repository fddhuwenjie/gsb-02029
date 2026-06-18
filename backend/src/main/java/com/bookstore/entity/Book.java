package com.bookstore.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "books")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "category", "seller"})
public class Book {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String title;
    
    private String author;
    private String isbn;
    private String publisher;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "cover_image")
    private String coverImage;
    
    @Column(name = "original_price")
    private BigDecimal originalPrice;
    
    @Column(nullable = false)
    private BigDecimal price;
    
    private String quality;
    private Integer stock = 1;
    
    @Column(name = "category_id")
    private Long categoryId;
    
    @Column(name = "seller_id")
    private Long sellerId;
    
    private Integer status = 1;
    
    @Column(name = "view_count")
    private Integer viewCount = 0;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}
