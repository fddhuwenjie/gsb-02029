package com.bookstore.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class OrderRequest {
    
    @NotBlank(message = "收货地址不能为空")
    private String address;
    
    @NotBlank(message = "联系电话不能为空")
    private String phone;
    
    @NotBlank(message = "收货人不能为空")
    private String receiver;
    
    private String remark;
    
    @NotEmpty(message = "订单商品不能为空")
    private List<OrderItemRequest> items;
    
    @Data
    public static class OrderItemRequest {
        
        @NotNull(message = "书籍ID不能为空")
        private Long bookId;
        
        @Min(value = 1, message = "购买数量至少为1")
        private Integer quantity;
    }
}
