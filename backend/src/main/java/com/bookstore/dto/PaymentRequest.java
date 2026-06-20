package com.bookstore.dto;

import lombok.Data;

@Data
public class PaymentRequest {
    private Long orderId;
    private String paymentMethod;
    private String paymentNo;
    private String thirdPartyNo;
}
