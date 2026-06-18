package com.bookstore.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class BookRequest {
    
    @NotBlank(message = "书籍标题不能为空")
    private String title;
    
    @NotBlank(message = "书籍作者不能为空")
    private String author;
    
    private String description;
    
    @DecimalMin(value = "0.01", message = "价格必须大于0")
    private BigDecimal price;
    
    private BigDecimal originalPrice;
    
    @Min(value = 0, message = "库存不能为负数")
    private Integer stock;
    
    private String isbn;
    private String publisher;
    private String coverImage;
    private String quality;
    private Long categoryId;
}
