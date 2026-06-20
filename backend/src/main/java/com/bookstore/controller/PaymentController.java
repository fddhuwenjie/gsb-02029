package com.bookstore.controller;

import com.bookstore.constant.OrderStatus;
import com.bookstore.dto.ApiResponse;
import com.bookstore.dto.PaymentRequest;
import com.bookstore.entity.Order;
import com.bookstore.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/payment")
public class PaymentController {
    
    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);
    
    private final OrderService orderService;
    
    @Value("${payment.mode:mock}")
    private String paymentMode;
    
    @Value("${payment.wechat.appid:}")
    private String wechatAppId;
    
    @Value("${payment.wechat.mchid:}")
    private String wechatMchId;
    
    @Value("${payment.alipay.appid:}")
    private String alipayAppId;
    
    public PaymentController(OrderService orderService) {
        this.orderService = orderService;
    }
    
    @PostMapping("/create")
    public ApiResponse<?> createPayment(@RequestBody PaymentRequest request) {
        Order order = orderService.getOrderById(request.getOrderId());
        if (order == null) {
            return ApiResponse.error(404, "订单不存在");
        }
        
        if (!OrderStatus.PENDING.equals(order.getStatus())) {
            if (OrderStatus.PAID.equals(order.getStatus()) || 
                OrderStatus.SHIPPED.equals(order.getStatus()) || 
                OrderStatus.COMPLETED.equals(order.getStatus())) {
                Map<String, Object> result = new HashMap<>();
                result.put("orderId", order.getId());
                result.put("status", order.getStatus());
                result.put("message", "订单已支付");
                return ApiResponse.success(result);
            }
            return ApiResponse.error(400, "订单状态不正确: " + order.getStatus());
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("orderId", order.getId());
        result.put("orderNo", order.getOrderNo());
        result.put("amount", order.getTotalAmount());
        result.put("paymentMethod", request.getPaymentMethod());
        
        if ("mock".equals(paymentMode)) {
            result.put("mode", "mock");
            result.put("mockPaymentId", "MOCK_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            result.put("message", "模拟支付模式，点击确认即可完成支付");
        } else {
            result.put("mode", "production");
            if ("WECHAT".equals(request.getPaymentMethod())) {
                result.put("appId", wechatAppId);
                result.put("mchId", wechatMchId);
            } else if ("ALIPAY".equals(request.getPaymentMethod())) {
                result.put("appId", alipayAppId);
            }
        }
        
        return ApiResponse.success(result);
    }
    
    @PostMapping("/confirm")
    public ApiResponse<?> confirmPayment(@RequestBody PaymentRequest request) {
        try {
            String paymentNo = request.getPaymentNo();
            if (paymentNo == null || paymentNo.isEmpty()) {
                paymentNo = "MOCK_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
            }
            
            String payMethod = request.getPaymentMethod();
            if (payMethod == null || payMethod.isEmpty()) {
                payMethod = "MOCK";
            }
            
            Order order = orderService.payOrder(request.getOrderId(), paymentNo, payMethod);
            
            Map<String, Object> result = new HashMap<>();
            result.put("orderId", order.getId());
            result.put("orderNo", order.getOrderNo());
            result.put("status", order.getStatus());
            result.put("paymentNo", order.getPaymentNo());
            result.put("payTime", order.getPayTime());
            result.put("message", "支付成功");
            
            return ApiResponse.success(result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(404, e.getMessage());
        } catch (IllegalStateException e) {
            Order order = orderService.getOrderById(request.getOrderId());
            if (order != null && (OrderStatus.PAID.equals(order.getStatus()) || 
                OrderStatus.SHIPPED.equals(order.getStatus()) || 
                OrderStatus.COMPLETED.equals(order.getStatus()))) {
                Map<String, Object> result = new HashMap<>();
                result.put("orderId", order.getId());
                result.put("orderNo", order.getOrderNo());
                result.put("status", order.getStatus());
                result.put("paymentNo", order.getPaymentNo());
                result.put("payTime", order.getPayTime());
                result.put("message", "订单已支付");
                return ApiResponse.success(result);
            }
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PostMapping("/notify/wechat")
    public ApiResponse<?> wechatNotify(@RequestBody Map<String, String> notifyData) {
        log.info("收到微信支付回调: {}", notifyData);
        return handlePaymentNotify(notifyData, "WECHAT");
    }

    @PostMapping("/notify/alipay")
    public ApiResponse<?> alipayNotify(@RequestBody Map<String, String> notifyData) {
        log.info("收到支付宝支付回调: {}", notifyData);
        return handlePaymentNotify(notifyData, "ALIPAY");
    }

    private ApiResponse<?> handlePaymentNotify(Map<String, String> notifyData, String payMethod) {
        try {
            String orderIdStr = notifyData.get("out_trade_no");
            String paymentNo = notifyData.get("transaction_id");
            if (paymentNo == null) {
                paymentNo = notifyData.get("trade_no");
            }
            
            if (orderIdStr == null || paymentNo == null) {
                log.error("支付回调参数不完整");
                return ApiResponse.error(400, "参数不完整");
            }

            Long orderId = Long.parseLong(orderIdStr);
            
            try {
                orderService.payOrder(orderId, paymentNo, payMethod);
            } catch (IllegalStateException e) {
                Order order = orderService.getOrderById(orderId);
                if (order == null || !OrderStatus.PAID.equals(order.getStatus()) && 
                    !OrderStatus.SHIPPED.equals(order.getStatus()) && 
                    !OrderStatus.COMPLETED.equals(order.getStatus())) {
                    throw e;
                }
                log.info("订单 {} 已处理过支付回调，直接返回成功（幂等）", orderId);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("code", "SUCCESS");
            result.put("message", "处理成功");
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("处理支付回调失败", e);
            return ApiResponse.error(500, "处理失败");
        }
    }
    
    @GetMapping("/config")
    public ApiResponse<?> getPaymentConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("mode", paymentMode);
        config.put("wechatEnabled", wechatAppId != null && !wechatAppId.isEmpty());
        config.put("alipayEnabled", alipayAppId != null && !alipayAppId.isEmpty());
        return ApiResponse.success(config);
    }
}
