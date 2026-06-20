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
import org.springframework.beans.factory.annotation.Value;
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

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final BookRepository bookRepository;
    private final CartItemRepository cartItemRepository;

    @Value("${order.expire-minutes:30}")
    private int orderExpireMinutes;

    public OrderService(OrderRepository orderRepository, OrderItemRepository orderItemRepository,
                        BookRepository bookRepository, CartItemRepository cartItemRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.bookRepository = bookRepository;
        this.cartItemRepository = cartItemRepository;
    }

    public Page<Order> getBuyerOrders(Long userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return orderRepository.findByUserId(userId, pageRequest);
    }

    public Page<Order> getSellerOrders(Long sellerId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return orderRepository.findBySellerId(sellerId, pageRequest);
    }

    public Order getOrderById(Long id) {
        return orderRepository.findById(id).orElse(null);
    }

    @Transactional
    public Order createOrder(OrderRequest request, Long userId) {
        log.info("用户 {} 开始创建订单", userId);

        BigDecimal totalAmount = BigDecimal.ZERO;
        LocalDateTime now = LocalDateTime.now();

        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setAddress(request.getAddress());
        order.setPhone(request.getPhone());
        order.setReceiver(request.getReceiver());
        order.setRemark(request.getRemark());
        order.setStatus(OrderStatus.PENDING);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        order.setExpireAt(now.plusMinutes(orderExpireMinutes));

        for (OrderRequest.OrderItemRequest item : request.getItems()) {
            Book book = bookRepository.findById(item.getBookId()).orElse(null);
            if (book == null) {
                throw new IllegalArgumentException("书籍不存在: " + item.getBookId());
            }
            int updated = bookRepository.decrementStock(item.getBookId(), item.getQuantity());
            if (updated == 0) {
                throw new IllegalArgumentException("库存不足: " + book.getTitle());
            }
            totalAmount = totalAmount.add(book.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        order.setTotalAmount(totalAmount);
        orderRepository.save(order);

        log.info("订单 {} 创建成功，总金额: {}, 库存已预留, {}分钟后过期",
                order.getOrderNo(), totalAmount, orderExpireMinutes);

        for (OrderRequest.OrderItemRequest item : request.getItems()) {
            Book book = bookRepository.findById(item.getBookId()).orElse(null);
            if (book == null) continue;

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
    public Order markPaid(Long orderId, String paymentId, String paymentMethod) {
        log.info("处理支付成功回调: orderId={}, paymentId={}, method={}", orderId, paymentId, paymentMethod);

        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }

        if (OrderStatus.PAID.equals(order.getStatus())
                || OrderStatus.SHIPPED.equals(order.getStatus())
                || OrderStatus.COMPLETED.equals(order.getStatus())) {
            log.info("订单 {} 已处于终态 {}，幂等返回，不重复处理", orderId, order.getStatus());
            return order;
        }

        if (OrderStatus.CANCELLED.equals(order.getStatus())) {
            log.warn("订单 {} 已取消，拒绝支付回调 paymentId={}", orderId, paymentId);
            throw new IllegalStateException("订单已取消，无法支付");
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = orderRepository.casMarkPaid(orderId, paymentId, paymentMethod, now);
        if (updated == 0) {
            Order fresh = orderRepository.findById(orderId).orElse(null);
            if (fresh != null && (OrderStatus.PAID.equals(fresh.getStatus())
                    || OrderStatus.SHIPPED.equals(fresh.getStatus())
                    || OrderStatus.COMPLETED.equals(fresh.getStatus()))) {
                log.info("订单 {} 被并发请求先行处理为 PAID，幂等返回", orderId);
                return fresh;
            }
            throw new IllegalStateException("订单状态异常，支付失败");
        }

        order.setStatus(OrderStatus.PAID);
        order.setPaymentId(paymentId);
        order.setPaymentMethod(paymentMethod);
        order.setPaidAt(now);
        order.setUpdatedAt(now);
        log.info("订单 {} 支付成功，paymentId={}", orderId, paymentId);
        return order;
    }

    @Transactional
    public Order cancelOrder(Long orderId, String reason) {
        log.info("取消订单: orderId={}, reason={}", orderId, reason);

        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }

        if (OrderStatus.CANCELLED.equals(order.getStatus())) {
            log.info("订单 {} 已经是取消状态，幂等返回", orderId);
            return order;
        }
        if (OrderStatus.SHIPPED.equals(order.getStatus()) || OrderStatus.COMPLETED.equals(order.getStatus())) {
            throw new IllegalStateException("已发货或已完成的订单不能取消");
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = orderRepository.casCancel(orderId, now, reason);
        if (updated == 0) {
            Order fresh = orderRepository.findById(orderId).orElse(null);
            if (fresh != null && OrderStatus.CANCELLED.equals(fresh.getStatus())) {
                return fresh;
            }
            throw new IllegalStateException("订单状态不允许取消");
        }

        restoreStock(orderId);

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(now);
        order.setCancelReason(reason);
        order.setUpdatedAt(now);
        log.info("订单 {} 已取消，库存已归还", orderId);
        return order;
    }

    @Transactional
    public Order shipOrder(Long orderId, String trackingNo) {
        log.info("订单 {} 发货, trackingNo={}", orderId, trackingNo);

        if (trackingNo == null || trackingNo.trim().isEmpty()) {
            throw new IllegalArgumentException("快递单号不能为空");
        }

        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }

        if (OrderStatus.SHIPPED.equals(order.getStatus()) || OrderStatus.COMPLETED.equals(order.getStatus())) {
            log.info("订单 {} 已经发货或完成，幂等返回", orderId);
            return order;
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = orderRepository.casShip(orderId, trackingNo.trim(), now);
        if (updated == 0) {
            Order fresh = orderRepository.findById(orderId).orElse(null);
            if (fresh != null && (OrderStatus.SHIPPED.equals(fresh.getStatus()) || OrderStatus.COMPLETED.equals(fresh.getStatus()))) {
                return fresh;
            }
            throw new IllegalStateException("只有已付款订单才能发货");
        }

        order.setStatus(OrderStatus.SHIPPED);
        order.setTrackingNo(trackingNo.trim());
        order.setShippedAt(now);
        order.setUpdatedAt(now);
        return order;
    }

    @Transactional
    public Order completeOrder(Long orderId) {
        log.info("订单 {} 确认收货", orderId);

        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }

        if (OrderStatus.COMPLETED.equals(order.getStatus())) {
            return order;
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = orderRepository.casComplete(orderId, now);
        if (updated == 0) {
            Order fresh = orderRepository.findById(orderId).orElse(null);
            if (fresh != null && OrderStatus.COMPLETED.equals(fresh.getStatus())) {
                return fresh;
            }
            throw new IllegalStateException("只有已发货订单才能确认收货");
        }

        order.setStatus(OrderStatus.COMPLETED);
        order.setCompletedAt(now);
        order.setUpdatedAt(now);
        return order;
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public int cancelExpiredOrders() {
        LocalDateTime now = LocalDateTime.now();
        List<Order> expired = orderRepository.findExpiredOrders(now);
        if (expired.isEmpty()) {
            return 0;
        }
        log.info("发现 {} 个超时未支付订单，开始自动取消", expired.size());
        int count = 0;
        for (Order o : expired) {
            try {
                cancelOrder(o.getId(), "超时未支付自动取消");
                count++;
            } catch (Exception e) {
                log.warn("自动取消订单 {} 失败: {}", o.getId(), e.getMessage());
            }
        }
        return count;
    }

    private void restoreStock(Long orderId) {
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        for (OrderItem item : items) {
            int ret = bookRepository.incrementStock(item.getBookId(), item.getQuantity());
            if (ret > 0) {
                log.info("书籍 {} 库存恢复 +{}", item.getBookId(), item.getQuantity());
            }
        }
    }

    private String generateOrderNo() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}
