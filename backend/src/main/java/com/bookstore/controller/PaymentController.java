package com.bookstore.controller;

import com.bookstore.constant.OrderStatus;
import com.bookstore.dto.ApiResponse;
import com.bookstore.dto.PaymentRequest;
import com.bookstore.entity.Order;
import com.bookstore.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/payment")
public class PaymentController {
    
    private final OrderRepository orderRepository;
    
    @Value("${payment.mode:mock}")
    private String paymentMode;
    
    @Value("${payment.wechat.appid:}")
    private String wechatAppId;
    
    @Value("${payment.wechat.mchid:}")
    private String wechatMchId;
    
    @Value("${payment.alipay.appid:}")
    private String alipayAppId;
    
    public PaymentController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }
    
    @PostMapping("/create")
    public ApiResponse<?> createPayment(@RequestBody PaymentRequest request) {
        Order order = orderRepository.findById(request.getOrderId()).orElse(null);
        if (order == null) {
            return ApiResponse.error(404, "订单不存在");
        }
        
        if (!OrderStatus.PENDING.equals(order.getStatus())) {
            return ApiResponse.error(400, "订单状态不正确");
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("orderId", order.getId());
        result.put("orderNo", order.getOrderNo());
        result.put("amount", order.getTotalAmount());
        result.put("paymentMethod", request.getPaymentMethod());
        
        if ("mock".equals(paymentMode)) {
            // 模拟支付模式
            result.put("mode", "mock");
            result.put("mockPaymentId", "MOCK_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            result.put("message", "模拟支付模式，点击确认即可完成支付");
        } else {
            // 生产模式 - 返回真实支付参数
            result.put("mode", "production");
            if ("WECHAT".equals(request.getPaymentMethod())) {
                result.put("appId", wechatAppId);
                result.put("mchId", wechatMchId);
                // 实际生产环境需要调用微信支付API生成预支付订单
            } else if ("ALIPAY".equals(request.getPaymentMethod())) {
                result.put("appId", alipayAppId);
                // 实际生产环境需要调用支付宝API生成支付表单
            }
        }
        
        return ApiResponse.success(result);
    }
    
    @PostMapping("/confirm")
    public ApiResponse<?> confirmPayment(@RequestBody PaymentRequest request) {
        Order order = orderRepository.findById(request.getOrderId()).orElse(null);
        if (order == null) {
            return ApiResponse.error(404, "订单不存在");
        }
        
        if (!OrderStatus.PENDING.equals(order.getStatus())) {
            return ApiResponse.error(400, "订单已支付或已取消");
        }
        
        order.setStatus(OrderStatus.PAID);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        
        Map<String, Object> result = new HashMap<>();
        result.put("orderId", order.getId());
        result.put("status", OrderStatus.PAID);
        result.put("message", "支付成功");
        
        return ApiResponse.success(result);
    }
    
    @GetMapping("/config")
    public ApiResponse<?> getPaymentConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("mode", paymentMode);
        config.put("wechatEnabled", !wechatAppId.isEmpty());
        config.put("alipayEnabled", !alipayAppId.isEmpty());
        return ApiResponse.success(config);
    }
}
