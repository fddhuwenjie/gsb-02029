package com.bookstore.controller;

import com.bookstore.constant.OrderStatus;
import com.bookstore.dto.ApiResponse;
import com.bookstore.dto.PaymentRequest;
import com.bookstore.entity.Order;
import com.bookstore.repository.OrderRepository;
import com.bookstore.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

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

        if (OrderStatus.CANCELLED.equals(order.getStatus())) {
            return ApiResponse.error(400, "订单已取消");
        }
        if (!OrderStatus.PENDING.equals(order.getStatus())) {
            Map<String, Object> result = new HashMap<>();
            result.put("orderId", order.getId());
            result.put("status", order.getStatus());
            result.put("message", "订单当前状态: " + order.getStatus());
            return ApiResponse.success(result);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("orderId", order.getId());
        result.put("orderNo", order.getOrderNo());
        result.put("amount", order.getTotalAmount());
        result.put("paymentMethod", request.getPaymentMethod());
        result.put("expireAt", order.getExpireAt());

        if ("mock".equals(paymentMode)) {
            result.put("mode", "mock");
            result.put("mockPaymentId", "MOCK_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
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
        Order order = orderRepository.findById(request.getOrderId()).orElse(null);
        if (order == null) {
            return ApiResponse.error(404, "订单不存在");
        }

        String paymentId;
        if ("mock".equals(paymentMode)) {
            paymentId = request.getPaymentId() != null ? request.getPaymentId()
                    : "MOCK_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        } else {
            paymentId = request.getPaymentId();
            if (paymentId == null || paymentId.isEmpty()) {
                return ApiResponse.error(400, "生产模式必须提供第三方支付流水号");
            }
        }

        try {
            Order paid = orderService.markPaid(request.getOrderId(), paymentId, request.getPaymentMethod());
            Map<String, Object> result = new HashMap<>();
            result.put("orderId", paid.getId());
            result.put("status", paid.getStatus());
            result.put("paymentId", paid.getPaymentId());
            result.put("message", "支付成功");
            return ApiResponse.success(result);
        } catch (IllegalStateException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(404, e.getMessage());
        }
    }

    @PostMapping("/callback/wechat")
    public ApiResponse<?> wechatCallback(@RequestBody Map<String, String> body) {
        return handlePaymentCallback(body.get("out_trade_no"), body.get("transaction_id"), "WECHAT");
    }

    @PostMapping("/callback/alipay")
    public ApiResponse<?> alipayCallback(@RequestBody Map<String, String> body) {
        return handlePaymentCallback(body.get("out_trade_no"), body.get("trade_no"), "ALIPAY");
    }

    private ApiResponse<?> handlePaymentCallback(String outTradeNo, String transactionId, String method) {
        log.info("收到{}支付回调: outTradeNo={}, transactionId={}", method, outTradeNo, transactionId);
        if (transactionId == null || transactionId.isEmpty()) {
            log.warn("{}回调缺少交易号", method);
            return ApiResponse.error(400, "缺少交易号");
        }

        Long orderId;
        try {
            orderId = Long.parseLong(outTradeNo);
        } catch (NumberFormatException e) {
            log.warn("{}回调订单号格式错误: {}", method, outTradeNo);
            return ApiResponse.error(400, "订单号格式错误");
        }

        try {
            orderService.markPaid(orderId, transactionId, method);
            Map<String, Object> result = new HashMap<>();
            result.put("code", "SUCCESS");
            result.put("message", "处理成功");
            return ApiResponse.success(result);
        } catch (IllegalStateException e) {
            log.warn("{}回调处理失败 orderId={}: {}", method, orderId, e.getMessage());
            return ApiResponse.error(400, e.getMessage());
        }
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
