package com.bookstore.service;

import com.bookstore.constant.OrderStatus;
import com.bookstore.dto.OrderRequest;
import com.bookstore.entity.Book;
import com.bookstore.entity.Order;
import com.bookstore.entity.OrderItem;
import com.bookstore.repository.BookRepository;
import com.bookstore.repository.CartItemRepository;
import com.bookstore.repository.OrderItemRepository;
import com.bookstore.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {
    
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final int ORDER_EXPIRE_MINUTES = 30;
    
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final BookRepository bookRepository;
    private final CartItemRepository cartItemRepository;
    
    public OrderService(OrderRepository orderRepository, OrderItemRepository orderItemRepository,
                      BookRepository bookRepository, CartItemRepository cartItemRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.bookRepository = bookRepository;
        this.cartItemRepository = cartItemRepository;
    }
    
    public Page<Order> getOrders(Long userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return orderRepository.findByUserId(userId, pageRequest);
    }

    public Page<Order> getSellerOrders(Long sellerId, int page, int size, String status) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (status != null && !status.isEmpty()) {
            return orderRepository.findBySellerIdAndStatus(sellerId, status, pageRequest);
        }
        return orderRepository.findBySellerId(sellerId, pageRequest);
    }
    
    public Order getOrderById(Long id) {
        return orderRepository.findById(id).orElse(null);
    }
    
    @Transactional
    public Order createOrder(OrderRequest request, Long userId) {
        log.info("用户 {} 开始创建订单", userId);
        
        BigDecimal totalAmount = BigDecimal.ZERO;

        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setAddress(request.getAddress());
        order.setPhone(request.getPhone());
        order.setReceiver(request.getReceiver());
        order.setRemark(request.getRemark());
        order.setStatus(OrderStatus.PENDING);
        order.setExpireTime(LocalDateTime.now().plusMinutes(ORDER_EXPIRE_MINUTES));

        for (OrderRequest.OrderItemRequest item : request.getItems()) {
            Book book = bookRepository.findById(item.getBookId()).orElse(null);
            if (book == null) {
                throw new IllegalArgumentException("书籍不存在: " + item.getBookId());
            }
            totalAmount = totalAmount.add(book.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        order.setTotalAmount(totalAmount);
        order = orderRepository.save(order);
        
        log.info("订单 {} 创建成功，总金额: {}, 过期时间: {}", order.getOrderNo(), totalAmount, order.getExpireTime());
        
        for (OrderRequest.OrderItemRequest item : request.getItems()) {
            Book book = bookRepository.findById(item.getBookId()).orElse(null);
            
            OrderItem orderItem = new OrderItem();
            orderItem.setOrderId(order.getId());
            orderItem.setBookId(item.getBookId());
            orderItem.setQuantity(item.getQuantity());
            orderItem.setPrice(book.getPrice());
            orderItemRepository.save(orderItem);
        }
        
        cartItemRepository.deleteByUserId(userId);
        log.info("用户 {} 购物车已清空", userId);
        
        return order;
    }

    @Transactional
    public Order payOrder(Long orderId, String paymentNo, String payMethod) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }

        if (OrderStatus.PAID.equals(order.getStatus()) || 
            OrderStatus.SHIPPED.equals(order.getStatus()) || 
            OrderStatus.COMPLETED.equals(order.getStatus())) {
            log.info("订单 {} 已经支付过，直接返回（幂等处理）", orderId);
            return order;
        }

        if (!OrderStatus.PENDING.equals(order.getStatus())) {
            throw new IllegalStateException("订单状态不正确，无法支付: " + order.getStatus());
        }

        if (order.getExpireTime() != null && LocalDateTime.now().isAfter(order.getExpireTime())) {
            cancelOrderInternal(order);
            throw new IllegalStateException("订单已超时，请重新下单");
        }

        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        for (OrderItem item : items) {
            int updated = bookRepository.decrementStock(item.getBookId(), item.getQuantity());
            if (updated == 0) {
                Book book = bookRepository.findById(item.getBookId()).orElse(null);
                throw new IllegalStateException("库存不足: " + (book != null ? book.getTitle() : item.getBookId()));
            }
        }

        LocalDateTime now = LocalDateTime.now();
        int updatedRows = orderRepository.markAsPaid(
            orderId, 
            OrderStatus.PENDING, 
            OrderStatus.PAID,
            now,
            paymentNo,
            payMethod,
            now
        );

        if (updatedRows == 0) {
            log.warn("订单 {} 支付状态更新失败，可能已被其他请求处理", orderId);
            rollbackStock(items);
            throw new IllegalStateException("订单处理中，请稍后刷新查看");
        }

        log.info("订单 {} 支付成功，支付流水号: {}", orderId, paymentNo);
        return orderRepository.findById(orderId).orElse(order);
    }

    @Transactional
    public Order cancelOrder(Long id) {
        Order order = orderRepository.findById(id).orElse(null);
        if (order == null) {
            log.warn("取消订单失败，订单 {} 不存在", id);
            throw new IllegalArgumentException("订单不存在");
        }
        return cancelOrderInternal(order);
    }

    private Order cancelOrderInternal(Order order) {
        if (OrderStatus.CANCELLED.equals(order.getStatus())) {
            log.info("订单 {} 已经是取消状态，直接返回（幂等处理）", order.getId());
            return order;
        }

        if (!OrderStatus.PENDING.equals(order.getStatus())) {
            throw new IllegalStateException("当前状态不允许取消: " + order.getStatus());
        }

        LocalDateTime now = LocalDateTime.now();
        int updatedRows = orderRepository.cancelOrder(
            order.getId(),
            OrderStatus.PENDING,
            OrderStatus.CANCELLED,
            now,
            now
        );

        if (updatedRows == 0) {
            log.warn("订单 {} 取消失败，可能已被其他请求处理", order.getId());
            throw new IllegalStateException("订单处理中，请稍后刷新查看");
        }

        log.info("订单 {} 已取消", order.getId());
        return orderRepository.findById(order.getId()).orElse(order);
    }

    @Transactional
    public Order shipOrder(Long id, String trackingNo) {
        Order order = orderRepository.findById(id).orElse(null);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }

        if (!OrderStatus.PAID.equals(order.getStatus())) {
            throw new IllegalStateException("订单状态不正确，无法发货: " + order.getStatus());
        }

        LocalDateTime now = LocalDateTime.now();
        int updatedRows = orderRepository.markAsShipped(
            id,
            OrderStatus.PAID,
            OrderStatus.SHIPPED,
            now,
            now,
            trackingNo
        );

        if (updatedRows == 0) {
            throw new IllegalStateException("订单处理中，请稍后刷新查看");
        }

        log.info("订单 {} 已发货，物流单号: {}", id, trackingNo);
        return orderRepository.findById(id).orElse(order);
    }

    @Transactional
    public Order completeOrder(Long id) {
        Order order = orderRepository.findById(id).orElse(null);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }

        if (!OrderStatus.SHIPPED.equals(order.getStatus())) {
            throw new IllegalStateException("订单状态不正确，无法确认收货: " + order.getStatus());
        }

        LocalDateTime now = LocalDateTime.now();
        int updatedRows = orderRepository.markAsCompleted(
            id,
            OrderStatus.SHIPPED,
            OrderStatus.COMPLETED,
            now,
            now
        );

        if (updatedRows == 0) {
            throw new IllegalStateException("订单处理中，请稍后刷新查看");
        }

        log.info("订单 {} 已完成", id);
        return orderRepository.findById(id).orElse(order);
    }

    private void rollbackStock(List<OrderItem> items) {
        for (OrderItem item : items) {
            bookRepository.incrementStock(item.getBookId(), item.getQuantity());
            log.info("回滚库存，书籍: {}, 数量: {}", item.getBookId(), item.getQuantity());
        }
    }

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void cancelExpiredOrders() {
        LocalDateTime threshold = LocalDateTime.now();
        List<Order> expiredOrders = orderRepository.findByStatusAndExpireTimeBefore(OrderStatus.PENDING, threshold);
        
        for (Order order : expiredOrders) {
            try {
                log.info("自动取消超时订单: {}", order.getOrderNo());
                cancelOrderInternal(order);
            } catch (Exception e) {
                log.error("自动取消订单 {} 失败: {}", order.getOrderNo(), e.getMessage());
            }
        }
    }
    
    private String generateOrderNo() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) 
               + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}
