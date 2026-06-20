package com.bookstore.controller;

import com.bookstore.constant.OrderStatus;
import com.bookstore.dto.ApiResponse;
import com.bookstore.dto.PaymentRequest;
import com.bookstore.entity.Order;
import com.bookstore.service.OrderService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/payment")
public class PaymentController {

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
    public ApiResponse<?> createPayment(@RequestBody PaymentRequest request,
                                         @AuthenticationPrincipal Long userId) {
        try {
            String paymentMethod = request.getPaymentMethod() != null ? request.getPaymentMethod() : "MOCK";
            OrderService.PaymentInitiation initiation = orderService.initiatePayment(
                    request.getOrderId(), paymentMethod, userId);

            if (initiation.alreadyPaid) {
                Map<String, Object> result = new HashMap<>();
                result.put("orderId", initiation.orderId);
                result.put("orderNo", initiation.orderNo);
                result.put("paymentNo", initiation.paymentNo);
                result.put("status", OrderStatus.PAID);
                result.put("alreadyPaid", true);
                result.put("message", "订单已支付");
                return ApiResponse.success(result);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("orderId", initiation.orderId);
            result.put("orderNo", initiation.orderNo);
            result.put("paymentNo", initiation.paymentNo);
            result.put("paymentMethod", initiation.paymentMethod);
            result.put("amount", initiation.amount);
            result.put("mode", initiation.mode);
            result.put("message", initiation.message);

            if ("mock".equals(paymentMode) || "MOCK".equals(paymentMethod)) {
                result.put("mockPaymentId", "MOCK_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            } else {
                if ("WECHAT".equals(paymentMethod)) {
                    result.put("appId", wechatAppId);
                    result.put("mchId", wechatMchId);
                } else if ("ALIPAY".equals(paymentMethod)) {
                    result.put("appId", alipayAppId);
                }
            }

            return ApiResponse.success(result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PostMapping("/confirm")
    public ApiResponse<?> confirmPayment(@RequestBody PaymentRequest request,
                                          @AuthenticationPrincipal Long userId) {
        try {
            String paymentMethod = request.getPaymentMethod() != null ? request.getPaymentMethod() : "MOCK";

            if ("MOCK".equals(paymentMethod)) {
                Order order = orderService.mockPay(request.getOrderId(), userId);
                Map<String, Object> result = new HashMap<>();
                result.put("orderId", order.getId());
                result.put("orderNo", order.getOrderNo());
                result.put("status", order.getStatus());
                result.put("payTime", order.getPayTime());
                result.put("message", "支付成功");
                return ApiResponse.success(result);
            } else {
                Order order = orderService.confirmPayment(
                        request.getOrderId(),
                        request.getPaymentNo(),
                        paymentMethod,
                        request.getThirdPartyNo()
                );
                Map<String, Object> result = new HashMap<>();
                result.put("orderId", order.getId());
                result.put("orderNo", order.getOrderNo());
                result.put("status", order.getStatus());
                result.put("payTime", order.getPayTime());
                result.put("message", "支付成功");
                return ApiResponse.success(result);
            }
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (IllegalStateException e) {
            return ApiResponse.error(409, e.getMessage());
        }
    }

    @PostMapping(value = "/notify/wechat", produces = "application/xml;charset=UTF-8")
    public String wechatNotify(@RequestBody String notifyData) {
        return processWechatNotify(notifyData);
    }

    @PostMapping(value = "/notify/alipay", produces = "text/plain;charset=UTF-8")
    public String alipayNotify(@RequestBody String notifyData) {
        return processAlipayNotify(notifyData);
    }

    @PostMapping(value = "/notify/alipay/form", consumes = "application/x-www-form-urlencoded", produces = "text/plain;charset=UTF-8")
    public String alipayNotifyForm(@RequestParam Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (sb.length() > 0) sb.append("&");
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return processAlipayNotify(sb.toString());
    }

    private String processWechatNotify(String notifyData) {
        try {
            Map<String, String> fields = parseXmlFields(notifyData);

            String resultCode = fields.getOrDefault("result_code", "");
            String returnCode = fields.getOrDefault("return_code", "");
            if (!"SUCCESS".equals(returnCode) || !"SUCCESS".equals(resultCode)) {
                return buildWechatFailResponse("支付结果非成功");
            }

            String paymentNo = fields.get("out_trade_no");
            String thirdPartyNo = fields.get("transaction_id");

            if (paymentNo == null || paymentNo.isBlank()) {
                return buildWechatFailResponse("缺少商户订单号out_trade_no");
            }

            orderService.confirmPaymentByPaymentNo(paymentNo, "WECHAT", thirdPartyNo);
            return buildWechatSuccessResponse();
        } catch (IllegalArgumentException e) {
            return buildWechatFailResponse(e.getMessage());
        } catch (Exception e) {
            return buildWechatFailResponse("处理失败: " + e.getMessage());
        }
    }

    private String processAlipayNotify(String notifyData) {
        try {
            Map<String, String> params = parseUrlEncodedOrXml(notifyData);

            String tradeStatus = params.getOrDefault("trade_status", "");
            if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
                return "failure";
            }

            String paymentNo = params.get("out_trade_no");
            String thirdPartyNo = params.get("trade_no");

            if (paymentNo == null || paymentNo.isBlank()) {
                return "failure";
            }

            orderService.confirmPaymentByPaymentNo(paymentNo, "ALIPAY", thirdPartyNo);
            return "success";
        } catch (Exception e) {
            return "failure";
        }
    }

    private Map<String, String> parseXmlFields(String xml) {
        Map<String, String> result = new HashMap<>();
        if (xml == null) return result;
        Pattern pattern = Pattern.compile("<([a-zA-Z0-9_]+)><!\\[CDATA\\[(.*?)\\]\\]></\\1>|<([a-zA-Z0-9_]+)>([^<]+)</\\3>");
        Matcher matcher = pattern.matcher(xml);
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                result.put(matcher.group(1), matcher.group(2));
            } else if (matcher.group(3) != null) {
                result.put(matcher.group(3), matcher.group(4).trim());
            }
        }
        return result;
    }

    private Map<String, String> parseUrlEncodedOrXml(String data) {
        Map<String, String> result = new HashMap<>();
        if (data == null) return result;
        String trimmed = data.trim();
        if (trimmed.startsWith("<")) {
            return parseXmlFields(trimmed);
        }
        String[] pairs = trimmed.split("&");
        for (String pair : pairs) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                String key = java.net.URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
                String value = java.net.URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                result.put(key, value);
            }
        }
        return result;
    }

    private String buildWechatSuccessResponse() {
        return "<xml><return_code><![CDATA[SUCCESS]]></return_code><return_msg><![CDATA[OK]]></return_msg></xml>";
    }

    private String buildWechatFailResponse(String msg) {
        String safeMsg = msg != null ? msg.replaceAll("[<>&]", "") : "FAIL";
        return "<xml><return_code><![CDATA[FAIL]]></return_code><return_msg><![CDATA[" + safeMsg + "]]></return_msg></xml>";
    }

    @GetMapping("/config")
    public ApiResponse<?> getPaymentConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("mode", paymentMode);
        config.put("wechatEnabled", wechatAppId != null && !wechatAppId.isEmpty());
        config.put("alipayEnabled", alipayAppId != null && !alipayAppId.isEmpty());
        config.put("orderTimeoutMinutes", 15);
        return ApiResponse.success(config);
    }
}
