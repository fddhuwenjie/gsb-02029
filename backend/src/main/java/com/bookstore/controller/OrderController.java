package com.bookstore.controller;

import com.bookstore.dto.ApiResponse;
import com.bookstore.dto.OrderRequest;
import com.bookstore.entity.Order;
import com.bookstore.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public ApiResponse<?> getBuyerOrders(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<Order> orders = orderService.getBuyerOrders(userId, page, size);
        return ApiResponse.success(orders);
    }

    @GetMapping("/seller")
    public ApiResponse<?> getSellerOrders(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<Order> orders = orderService.getSellerOrders(userId, page, size);
        return ApiResponse.success(orders);
    }

    @GetMapping("/{id}")
    public ApiResponse<?> getOrder(@PathVariable Long id) {
        Order order = orderService.getOrderById(id);
        if (order == null) {
            return ApiResponse.error(404, "订单不存在");
        }
        return ApiResponse.success(order);
    }

    @PostMapping
    public ApiResponse<?> createOrder(
            @Valid @RequestBody OrderRequest request,
            @AuthenticationPrincipal Long userId) {
        try {
            Order order = orderService.createOrder(request, userId);
            return ApiResponse.success(order);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PutMapping("/{id}/cancel")
    public ApiResponse<?> cancelOrder(@PathVariable Long id) {
        try {
            Order order = orderService.cancelOrder(id, "用户主动取消");
            return ApiResponse.success(order);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PutMapping("/{id}/confirm")
    public ApiResponse<?> confirmReceive(@PathVariable Long id) {
        try {
            Order order = orderService.completeOrder(id);
            return ApiResponse.success(order);
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
}
