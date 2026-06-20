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

/**
 * 订单领域服务：
 *
 *  - 创建订单时同步预占库存（决定性瓶颈：库存原子扣减 SQL）
 *  - 支付完成走 CAS UPDATE，自带幂等（重复点击 / 回调重试 / 刷新重提均安全）
 *  - 取消 / 超时关单 走 CAS UPDATE，并补偿性归还库存，保证不会重复释放
 *  - 状态迁移统一走 OrderStatus 状态机，禁止状态回退或越权
 */
@Service
public class OrderService {
    
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final BookRepository bookRepository;
    private final CartItemRepository cartItemRepository;

    /**
     * 待付款订单超时（分钟），超过则系统自动取消并释放库存。
     */
    @Value("${order.pending-timeout-minutes:30}")
    private long pendingTimeoutMinutes;
    
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

        for (OrderRequest.OrderItemRequest item : request.getItems()) {
            Book book = bookRepository.findById(item.getBookId()).orElse(null);
            if (book == null) {
                throw new IllegalArgumentException("书籍不存在");
            }

            int updated = bookRepository.decrementStock(item.getBookId(), item.getQuantity());
            if (updated == 0) {
                throw new IllegalArgumentException("库存不足: " + book.getTitle());
            }

            totalAmount = totalAmount.add(book.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        order.setTotalAmount(totalAmount);
        orderRepository.save(order);
        
        log.info("订单 {} 创建成功，总金额: {}", order.getOrderNo(), totalAmount);
        
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

    /**
     * 支付完成：幂等。
     *
     *  - 首次：将订单从 PENDING 推进到 PAID，写入 paymentNo / paidAt
     *  - 重入（重复点击 / 回调重试 / 刷新重提）：返回当前订单状态而不重复扣款
     *  - 状态非法（例如已 CANCELLED / SHIPPED）：抛出 IllegalArgumentException
     */
    @Transactional
    public Order markPaid(Long orderId, String paymentNo, String paymentMethod) {
        if (orderId == null) {
            throw new IllegalArgumentException("订单ID不能为空");
        }
        if (paymentNo == null || paymentNo.isBlank()) {
            throw new IllegalArgumentException("支付流水号不能为空");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在"));

        // 幂等场景 A：同一笔 paymentNo 已经被处理过 —— 直接返回，避免重复扣款 / 重复扭转状态
        if (paymentNo.equals(order.getPaymentNo())) {
            log.info("订单 {} 收到重复支付通知，paymentNo={}，按幂等处理", orderId, paymentNo);
            return order;
        }

        // 幂等场景 B：订单已经付过款（来源不同支付渠道的回调）——拒绝二次写入但不报错
        if (OrderStatus.PAID.equals(order.getStatus())
                || OrderStatus.SHIPPED.equals(order.getStatus())
                || OrderStatus.COMPLETED.equals(order.getStatus())) {
            log.warn("订单 {} 已是 {} 状态，忽略新的支付请求 paymentNo={}",
                    orderId, order.getStatus(), paymentNo);
            return order;
        }

        if (OrderStatus.CANCELLED.equals(order.getStatus())) {
            throw new IllegalArgumentException("订单已取消，无法支付");
        }

        if (!OrderStatus.PENDING.equals(order.getStatus())) {
            throw new IllegalArgumentException("订单状态非法: " + order.getStatus());
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = orderRepository.markPaidIfPending(orderId, paymentNo, paymentMethod, now);
        if (updated == 0) {
            // 走到这里只可能是另一并发请求抢先把订单从 PENDING 推走了：
            // 重新读取最新状态，按幂等返回，避免抛错给到用户造成困惑
            Order latest = orderRepository.findById(orderId).orElseThrow();
            log.info("订单 {} 支付 CAS 失败，最新状态={}，按幂等返回", orderId, latest.getStatus());
            return latest;
        }

        log.info("订单 {} 支付成功，paymentNo={}, method={}", orderId, paymentNo, paymentMethod);
        return orderRepository.findById(orderId).orElseThrow();
    }

    /**
     * 用户主动取消订单（仅 PENDING 允许）。
     * 必须以 CAS UPDATE 完成"状态翻转 + 返还库存"，避免重复返还。
     */
    @Transactional
    public Order cancelOrder(Long id) {
        return cancelOrder(id, false);
    }

    /**
     * @param adminCancel true 表示来自后台，可以取消已支付未发货订单（PAID）；
     *                    false 表示用户取消，仅 PENDING 可取消。
     */
    @Transactional
    public Order cancelOrder(Long id, boolean adminCancel) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在"));

        if (OrderStatus.CANCELLED.equals(order.getStatus())) {
            log.info("订单 {} 已是 CANCELLED，按幂等返回", id);
            return order;
        }

        List<String> allowedFrom = adminCancel
                ? List.of(OrderStatus.PENDING, OrderStatus.PAID)
                : List.of(OrderStatus.PENDING);

        int updated = orderRepository.cancelIfStatusIn(id, allowedFrom, LocalDateTime.now());
        if (updated == 0) {
            // 并发：他人已抢先取消 / 推进
            Order latest = orderRepository.findById(id).orElseThrow();
            if (OrderStatus.CANCELLED.equals(latest.getStatus())) {
                return latest;
            }
            throw new IllegalArgumentException("订单当前状态不允许取消: " + latest.getStatus());
        }

        // 取消成功，归还库存（每个订单只会有 1 次 CAS 命中，因此每个 item 的库存只会被归还 1 次）
        List<OrderItem> items = orderItemRepository.findByOrderId(id);
        for (OrderItem item : items) {
            bookRepository.incrementStock(item.getBookId(), item.getQuantity());
        }
        log.info("订单 {} 已取消并归还库存，items={}", id, items.size());

        return orderRepository.findById(id).orElseThrow();
    }

    /**
     * 状态机驱动的通用迁移：用于管理端发货 / 完成。
     */
    @Transactional
    public Order transition(Long id, String targetStatus, String trackingNo) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在"));

        if (order.getStatus().equals(targetStatus)) {
            log.info("订单 {} 已经是目标状态 {}，按幂等返回", id, targetStatus);
            return order;
        }

        if (OrderStatus.CANCELLED.equals(targetStatus)) {
            // 走带库存归还的取消通道
            return cancelOrder(id, true);
        }

        if (!OrderStatus.canTransition(order.getStatus(), targetStatus)) {
            throw new IllegalArgumentException(
                    "非法状态迁移: " + order.getStatus() + " -> " + targetStatus);
        }

        int updated = orderRepository.transitionStatus(
                id, order.getStatus(), targetStatus, trackingNo, LocalDateTime.now());
        if (updated == 0) {
            // 并发：另一线程已经迁走了
            Order latest = orderRepository.findById(id).orElseThrow();
            if (latest.getStatus().equals(targetStatus)) {
                return latest;
            }
            throw new IllegalArgumentException(
                    "订单状态已被并发修改，当前状态: " + latest.getStatus());
        }
        return orderRepository.findById(id).orElseThrow();
    }

    /**
     * 定时扫描：将超时未付款的 PENDING 订单关闭并归还库存。
     * 关单也走 CAS，因此与用户主动取消、用户最后一刻支付的请求互斥。
     */
    @Scheduled(fixedDelayString = "${order.pending-timeout-scan-interval-ms:60000}")
    @Transactional
    public void expireTimeoutPendingOrders() {
        if (pendingTimeoutMinutes <= 0) {
            return;
        }
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(pendingTimeoutMinutes);
        List<Order> stale = orderRepository.findByStatusAndCreatedAtBefore(
                OrderStatus.PENDING, threshold);
        for (Order order : stale) {
            try {
                cancelOrder(order.getId(), false);
                log.info("超时未支付订单 {} 已自动关闭", order.getId());
            } catch (Exception e) {
                log.warn("自动关闭订单 {} 失败: {}", order.getId(), e.getMessage());
            }
        }
    }

    private String generateOrderNo() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) 
               + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}
