package com.bookstore.controller;

import com.bookstore.constant.OrderStatus;
import com.bookstore.dto.ApiResponse;
import com.bookstore.dto.PaymentRequest;
import com.bookstore.entity.Order;
import com.bookstore.repository.OrderRepository;
import com.bookstore.service.OrderService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 支付入口。Mock 与生产模式共用同一套订单状态流：
 *
 *   /payment/create   生成（或返回已存在的）支付预订单参数（不改变订单状态）
 *   /payment/confirm  Mock 模式下，前端确认支付 -> 走统一回调
 *   /payment/notify   生产模式下，第三方网关回调 -> 走统一回调
 *
 * 支付完成统一收敛到 OrderService#markPaid，由 SQL CAS 提供幂等保证：
 *   - 用户重复点击：第二次的 paymentNo 与第一次相同（同一会话），直接幂等返回
 *   - 网关回调重试：携带相同 paymentNo，markPaid 命中幂等分支
 *   - 不同回调先后到达：第一次成功后，paymentNo 落库，后续 CAS 命中 0 行
 */
@RestController
@RequestMapping("/api/payment")
public class PaymentController {
    
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    
    @Value("${payment.mode:mock}")
    private String paymentMode;
    
    @Value("${payment.wechat.appid:}")
    private String wechatAppId;
    
    @Value("${payment.wechat.mchid:}")
    private String wechatMchId;
    
    @Value("${payment.alipay.appid:}")
    private String alipayAppId;
    
    public PaymentController(OrderRepository orderRepository, OrderService orderService) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
    }
    
    @PostMapping("/create")
    public ApiResponse<?> createPayment(@RequestBody PaymentRequest request) {
        Order order = orderRepository.findById(request.getOrderId()).orElse(null);
        if (order == null) {
            return ApiResponse.error(404, "订单不存在");
        }

        // 已支付订单：返回已存在的 paymentNo，前端可幂等地完成（避免重复创建）
        if (OrderStatus.PAID.equals(order.getStatus())
                || OrderStatus.SHIPPED.equals(order.getStatus())
                || OrderStatus.COMPLETED.equals(order.getStatus())) {
            Map<String, Object> paid = new HashMap<>();
            paid.put("orderId", order.getId());
            paid.put("orderNo", order.getOrderNo());
            paid.put("amount", order.getTotalAmount());
            paid.put("status", order.getStatus());
            paid.put("paymentNo", order.getPaymentNo());
            paid.put("alreadyPaid", true);
            return ApiResponse.success(paid);
        }

        if (!OrderStatus.PENDING.equals(order.getStatus())) {
            return ApiResponse.error(400, "订单状态不正确: " + order.getStatus());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("orderId", order.getId());
        result.put("orderNo", order.getOrderNo());
        result.put("amount", order.getTotalAmount());
        result.put("paymentMethod", request.getPaymentMethod());
        
        if ("mock".equals(paymentMode)) {
            // 模拟支付：生成稳定的 paymentNo（按 orderNo 派生），让重复点击触发同一笔幂等键
            result.put("mode", "mock");
            String paymentNo = "MOCK_" + order.getOrderNo();
            result.put("mockPaymentId", paymentNo);
            result.put("message", "模拟支付模式，点击确认即可完成支付");
        } else {
            // 生产模式 - 返回真实支付参数（实际实现应调用网关下单获得 paymentNo）
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

    /**
     * Mock 模式下前端确认支付 —— 内部走与生产回调相同的入口，保证状态机统一。
     */
    @PostMapping("/confirm")
    public ApiResponse<?> confirmPayment(@RequestBody PaymentRequest request) {
        if (!"mock".equals(paymentMode)) {
            return ApiResponse.error(400, "生产模式不支持手动确认，请通过支付渠道回调");
        }
        if (request.getOrderId() == null) {
            return ApiResponse.error(400, "订单ID不能为空");
        }

        Order existing = orderRepository.findById(request.getOrderId()).orElse(null);
        if (existing == null) {
            return ApiResponse.error(404, "订单不存在");
        }
        // mock 模式下使用基于订单号的稳定 paymentNo —— 同一订单的多次确认都被识别为同一笔
        String paymentNo = "MOCK_" + existing.getOrderNo();
        return finalizePayment(request.getOrderId(), paymentNo, request.getPaymentMethod());
    }

    /**
     * 生产模式下，第三方支付网关异步通知（微信/支付宝）。
     * 真实场景应在此处校验签名、金额。所有重复回调都被 CAS 幂等收敛。
     */
    @PostMapping("/notify")
    public ApiResponse<?> paymentNotify(@RequestBody Map<String, Object> payload) {
        Object orderIdObj = payload.get("orderId");
        Object paymentNoObj = payload.get("paymentNo");
        Object methodObj = payload.get("paymentMethod");
        if (orderIdObj == null || paymentNoObj == null) {
            return ApiResponse.error(400, "回调参数缺失 orderId / paymentNo");
        }
        Long orderId = Long.valueOf(orderIdObj.toString());
        String paymentNo = paymentNoObj.toString();
        String method = methodObj != null ? methodObj.toString() : null;
        return finalizePayment(orderId, paymentNo, method);
    }

    private ApiResponse<?> finalizePayment(Long orderId, String paymentNo, String paymentMethod) {
        if (paymentNo == null || paymentNo.isBlank()) {
            paymentNo = "AUTO_" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        }
        Order order = orderService.markPaid(orderId, paymentNo, paymentMethod);

        Map<String, Object> result = new HashMap<>();
        result.put("orderId", order.getId());
        result.put("status", order.getStatus());
        result.put("paymentNo", order.getPaymentNo());
        result.put("paidAt", order.getPaidAt());
        // 统一文案：无论首单成功还是幂等命中，对调用方语义都是“支付成功”
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
