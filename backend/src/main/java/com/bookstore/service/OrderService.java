package com.bookstore.service;

import com.bookstore.constant.OrderStatus;
import com.bookstore.dto.OrderRequest;
import com.bookstore.entity.Book;
import com.bookstore.entity.Order;
import com.bookstore.entity.OrderItem;
import com.bookstore.entity.PaymentLog;
import com.bookstore.repository.BookRepository;
import com.bookstore.repository.CartItemRepository;
import com.bookstore.repository.OrderItemRepository;
import com.bookstore.repository.OrderRepository;
import com.bookstore.repository.PaymentLogRepository;
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
    private final PaymentLogRepository paymentLogRepository;

    @Value("${payment.order.timeout-minutes:15}")
    private int orderTimeoutMinutes;

    public OrderService(OrderRepository orderRepository, OrderItemRepository orderItemRepository,
                        BookRepository bookRepository, CartItemRepository cartItemRepository,
                        PaymentLogRepository paymentLogRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.bookRepository = bookRepository;
        this.cartItemRepository = cartItemRepository;
        this.paymentLogRepository = paymentLogRepository;
    }

    public Page<Order> getOrders(Long userId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return orderRepository.findByUserId(userId, pageRequest);
    }

    public Order getOrderById(Long id) {
        return orderRepository.findById(id).orElse(null);
    }

    public Order getOrderByOrderNo(String orderNo) {
        return orderRepository.findByOrderNo(orderNo).orElse(null);
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
        order.setExpireTime(LocalDateTime.now().plusMinutes(orderTimeoutMinutes));

        for (OrderRequest.OrderItemRequest item : request.getItems()) {
            Book book = bookRepository.findById(item.getBookId()).orElse(null);
            if (book == null) {
                throw new IllegalArgumentException("书籍不存在: " + item.getBookId());
            }
            if (book.getStatus() != 1) {
                throw new IllegalArgumentException("书籍已下架: " + book.getTitle());
            }

            int updated = bookRepository.decrementStock(item.getBookId(), item.getQuantity());
            if (updated == 0) {
                throw new IllegalArgumentException("库存不足: " + book.getTitle());
            }
            log.debug("书籍 {} 库存预扣 {} 成功", book.getTitle(), item.getQuantity());

            totalAmount = totalAmount.add(book.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        order.setTotalAmount(totalAmount);
        order = orderRepository.save(order);

        log.info("订单 {} 创建成功，总金额: {}，超时时间: {}", order.getOrderNo(), totalAmount, order.getExpireTime());

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
    public Order cancelOrder(Long orderId, Long userId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        if (userId != null && !order.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权操作此订单");
        }

        return doCancelOrCloseOrder(order, OrderStatus.CANCELLED, "用户取消");
    }

    @Transactional
    public PaymentInitiation initiatePayment(Long orderId, String paymentMethod, Long userId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        if (userId != null && !order.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权操作此订单");
        }

        if (OrderStatus.PAID_STATUSES.contains(order.getStatus())) {
            log.info("订单 {} 已支付，直接返回成功", orderId);
            return PaymentInitiation.alreadyPaid(order);
        }

        if (OrderStatus.FINISHED_STATUSES.contains(order.getStatus())) {
            throw new IllegalArgumentException("订单已" + order.getStatus() + "，无法支付");
        }

        if (isOrderExpired(order)) {
            doCancelOrCloseOrder(order, OrderStatus.CLOSED, "支付超时");
            throw new IllegalArgumentException("订单已超时，请重新下单");
        }

        LocalDateTime now = LocalDateTime.now();
        orderRepository.compareAndSetStatus(orderId, OrderStatus.PENDING, OrderStatus.PAYING, now);

        String paymentNo = generatePaymentNo();

        PaymentLog existingLog = paymentLogRepository.findByOrderIdAndStatus(orderId, "INITIATED");
        PaymentLog logEntry;
        if (existingLog != null) {
            logEntry = existingLog;
            logEntry.setPaymentNo(paymentNo);
            logEntry.setMethod(paymentMethod);
        } else {
            logEntry = new PaymentLog();
            logEntry.setOrderId(orderId);
            logEntry.setOrderNo(order.getOrderNo());
            logEntry.setPaymentNo(paymentNo);
            logEntry.setMethod(paymentMethod);
            logEntry.setAmount(order.getTotalAmount());
            logEntry.setStatus("INITIATED");
        }
        paymentLogRepository.save(logEntry);

        order = orderRepository.findById(orderId).orElse(order);
        return PaymentInitiation.of(order, paymentNo, paymentMethod);
    }

    @Transactional
    public Order confirmPayment(Long orderId, String paymentNo, String paymentMethod, String thirdPartyNo) {
        return confirmPaymentInternal(orderId, paymentNo, paymentMethod, thirdPartyNo);
    }

    @Transactional
    public Order confirmPaymentByPaymentNo(String paymentNo, String paymentMethod, String thirdPartyNo) {
        if (paymentNo == null || paymentNo.isBlank()) {
            throw new IllegalArgumentException("支付流水号不能为空");
        }
        PaymentLog log = paymentLogRepository.findByPaymentNo(paymentNo).orElse(null);
        Long resolvedOrderId = null;
        if (log != null) {
            resolvedOrderId = log.getOrderId();
        } else {
            Order order = orderRepository.findByPaymentNo(paymentNo).orElse(null);
            if (order != null) {
                resolvedOrderId = order.getId();
            }
        }
        if (resolvedOrderId == null) {
            throw new IllegalArgumentException("支付流水号对应的订单不存在: " + paymentNo);
        }
        return confirmPaymentInternal(resolvedOrderId, paymentNo, paymentMethod, thirdPartyNo);
    }

    private Order confirmPaymentInternal(Long orderId, String paymentNo, String paymentMethod, String thirdPartyNo) {
        String effectivePaymentNo = paymentNo;
        PaymentLog paymentLog = null;

        if (effectivePaymentNo != null) {
            paymentLog = paymentLogRepository.findByPaymentNo(effectivePaymentNo).orElse(null);
            if (paymentLog != null && "SUCCESS".equals(paymentLog.getStatus())) {
                log.info("支付流水 {} 已处理成功，幂等返回", effectivePaymentNo);
                return orderRepository.findById(paymentLog.getOrderId()).orElse(null);
            }
        }

        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }

        if (OrderStatus.PAID_STATUSES.contains(order.getStatus())) {
            log.info("订单 {} 已支付，幂等返回", orderId);
            ensurePaymentLogExists(order, effectivePaymentNo, paymentMethod, thirdPartyNo);
            return order;
        }

        if (OrderStatus.FINISHED_STATUSES.contains(order.getStatus())) {
            throw new IllegalArgumentException("订单已" + order.getStatus() + "，无法支付");
        }

        if (isOrderExpired(order)) {
            doCancelOrCloseOrder(order, OrderStatus.CLOSED, "支付超时");
            throw new IllegalArgumentException("订单已超时关闭");
        }

        if (effectivePaymentNo == null || effectivePaymentNo.isBlank()) {
            effectivePaymentNo = generatePaymentNo();
        }

        String effectiveMethod = paymentMethod != null ? paymentMethod : "MOCK";
        LocalDateTime now = LocalDateTime.now();

        int updated = orderRepository.markAsPaid(
                orderId,
                OrderStatus.PENDING,
                OrderStatus.PAID,
                effectivePaymentNo,
                effectiveMethod,
                now
        );

        if (updated == 0) {
            updated = orderRepository.markAsPaid(
                    orderId,
                    OrderStatus.PAYING,
                    OrderStatus.PAID,
                    effectivePaymentNo,
                    effectiveMethod,
                    now
            );
        }

        if (updated == 0) {
            Order currentOrder = orderRepository.findById(orderId).orElse(order);
            if (OrderStatus.PAID_STATUSES.contains(currentOrder.getStatus())) {
                log.info("并发支付请求，订单 {} 已被其他线程标记为已支付，幂等返回", orderId);
                ensurePaymentLogExists(currentOrder, effectivePaymentNo, effectiveMethod, thirdPartyNo);
                return currentOrder;
            }
            throw new IllegalStateException("订单支付状态更新失败，请刷新后重试");
        }

        saveOrUpdatePaymentLog(order, effectivePaymentNo, effectiveMethod, thirdPartyNo, now);

        Order paidOrder = orderRepository.findById(orderId).orElse(order);
        log.info("订单 {} 支付成功，支付流水: {}", order.getOrderNo(), effectivePaymentNo);
        return paidOrder;
    }

    private void ensurePaymentLogExists(Order order, String paymentNo, String method, String thirdPartyNo) {
        String effectivePaymentNo = paymentNo != null ? paymentNo : order.getPaymentNo();
        if (effectivePaymentNo == null) {
            return;
        }
        PaymentLog existing = paymentLogRepository.findByPaymentNo(effectivePaymentNo).orElse(null);
        if (existing != null && "SUCCESS".equals(existing.getStatus())) {
            return;
        }
        saveOrUpdatePaymentLog(order, effectivePaymentNo, method, thirdPartyNo, LocalDateTime.now());
    }

    private void saveOrUpdatePaymentLog(Order order, String paymentNo, String method, String thirdPartyNo, LocalDateTime now) {
        PaymentLog logEntry = paymentLogRepository.findByPaymentNo(paymentNo).orElse(null);
        if (logEntry == null) {
            logEntry = paymentLogRepository.findByOrderIdAndStatus(order.getId(), "INITIATED");
        }
        if (logEntry == null) {
            logEntry = new PaymentLog();
            logEntry.setOrderId(order.getId());
            logEntry.setOrderNo(order.getOrderNo());
        }
        logEntry.setPaymentNo(paymentNo);
        if (thirdPartyNo != null) {
            logEntry.setThirdPartyNo(thirdPartyNo);
        }
        if (method != null) {
            logEntry.setMethod(method);
        }
        if (logEntry.getAmount() == null) {
            logEntry.setAmount(order.getTotalAmount());
        }
        logEntry.setStatus("SUCCESS");
        logEntry.setRawResponse("Payment confirmed at " + now);
        paymentLogRepository.save(logEntry);
    }

    @Transactional
    public Order mockPay(Long orderId, Long userId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        if (userId != null && !order.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权操作此订单");
        }

        PaymentInitiation initiation = initiatePayment(orderId, "MOCK", userId);
        if (initiation.alreadyPaid) {
            return order;
        }
        return confirmPayment(orderId, initiation.paymentNo, "MOCK", "MOCK_" + UUID.randomUUID().toString().substring(0, 8));
    }

    @Transactional
    public Order shipOrder(Long orderId, String trackingNo) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }

        if (!OrderStatus.CAN_SHIP_STATUSES.contains(order.getStatus())) {
            throw new IllegalArgumentException("当前状态不允许发货: " + order.getStatus());
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = orderRepository.markAsShipped(orderId, OrderStatus.PAID, OrderStatus.SHIPPED, trackingNo, now);
        if (updated == 0) {
            Order current = orderRepository.findById(orderId).orElse(order);
            if (OrderStatus.SHIPPED.equals(current.getStatus()) || OrderStatus.COMPLETED.equals(current.getStatus())) {
                return current;
            }
            throw new IllegalStateException("发货状态更新失败");
        }

        log.info("订单 {} 已发货，物流单号: {}", order.getOrderNo(), trackingNo);
        return orderRepository.findById(orderId).orElse(order);
    }

    @Transactional
    public Order completeOrder(Long orderId, Long userId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        if (userId != null && !order.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权操作此订单");
        }

        if (!OrderStatus.CAN_COMPLETE_STATUSES.contains(order.getStatus())) {
            if (OrderStatus.COMPLETED.equals(order.getStatus())) {
                return order;
            }
            throw new IllegalArgumentException("当前状态不允许确认收货: " + order.getStatus());
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = orderRepository.compareAndSetStatus(orderId, OrderStatus.SHIPPED, OrderStatus.COMPLETED, now);
        if (updated == 0) {
            Order current = orderRepository.findById(orderId).orElse(order);
            if (OrderStatus.COMPLETED.equals(current.getStatus())) {
                return current;
            }
            throw new IllegalStateException("确认收货失败");
        }

        log.info("订单 {} 已确认收货，交易完成", order.getOrderNo());
        return orderRepository.findById(orderId).orElse(order);
    }

    @Transactional
    public Order adminUpdateOrderStatus(Long orderId, String newStatus, String trackingNo) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }

        if (!OrderStatus.canTransit(order.getStatus(), newStatus)) {
            throw new IllegalArgumentException("不允许从 " + order.getStatus() + " 变更为 " + newStatus);
        }

        if (OrderStatus.SHIPPED.equals(newStatus)) {
            return shipOrder(orderId, trackingNo);
        }

        if (OrderStatus.CANCELLED.equals(newStatus) || OrderStatus.CLOSED.equals(newStatus)) {
            if (OrderStatus.CAN_CANCEL_STATUSES.contains(order.getStatus())) {
                return doCancelOrCloseOrder(order, newStatus, "管理员操作");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = orderRepository.compareAndSetStatus(orderId, order.getStatus(), newStatus, now);
        if (updated == 0) {
            throw new IllegalStateException("状态更新失败，请刷新后重试");
        }

        return orderRepository.findById(orderId).orElse(order);
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 30000)
    @Transactional
    public void processExpiredOrders() {
        LocalDateTime now = LocalDateTime.now();
        List<Order> expiredOrders = orderRepository.findExpiredOrders(now);
        for (Order order : expiredOrders) {
            try {
                log.info("处理超时订单: {}", order.getOrderNo());
                doCancelOrCloseOrder(order, OrderStatus.CLOSED, "支付超时自动关闭");
            } catch (Exception e) {
                log.error("处理超时订单 {} 失败: {}", order.getOrderNo(), e.getMessage(), e);
            }
        }
    }

    private Order doCancelOrCloseOrder(Order order, String targetStatus, String reason) {
        if (!OrderStatus.CAN_CANCEL_STATUSES.contains(order.getStatus())) {
            if (targetStatus.equals(order.getStatus())) {
                return order;
            }
            throw new IllegalArgumentException("当前状态不允许取消/关闭: " + order.getStatus());
        }

        LocalDateTime now = LocalDateTime.now();

        int updated = orderRepository.compareAndSetStatus(
                order.getId(),
                order.getStatus(),
                targetStatus,
                now
        );

        if (updated == 0) {
            Order current = orderRepository.findById(order.getId()).orElse(order);
            if (targetStatus.equals(current.getStatus())) {
                return current;
            }
            if (OrderStatus.PAID_STATUSES.contains(current.getStatus())) {
                log.info("订单 {} 已支付，跳过{}", order.getOrderNo(), reason);
                return current;
            }
            throw new IllegalStateException("订单状态更新失败，可能已被其他操作处理");
        }

        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
        for (OrderItem item : items) {
            int restored = bookRepository.incrementStock(item.getBookId(), item.getQuantity());
            log.debug("书籍 {} 库存归还 {}，结果: {}", item.getBookId(), item.getQuantity(), restored);
        }

        log.info("订单 {} 已{}（{}），库存已归还", order.getOrderNo(),
                OrderStatus.CANCELLED.equals(targetStatus) ? "取消" : "关闭", reason);
        return orderRepository.findById(order.getId()).orElse(order);
    }

    private boolean isOrderExpired(Order order) {
        return order.getExpireTime() != null && LocalDateTime.now().isAfter(order.getExpireTime());
    }

    private String generateOrderNo() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private String generatePaymentNo() {
        return "PAY" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    public static class PaymentInitiation {
        public final Long orderId;
        public final String orderNo;
        public final String paymentNo;
        public final String paymentMethod;
        public final BigDecimal amount;
        public final boolean alreadyPaid;
        public final String mode;
        public final String message;

        private PaymentInitiation(Order order, String paymentNo, String paymentMethod, boolean alreadyPaid, String mode, String message) {
            this.orderId = order.getId();
            this.orderNo = order.getOrderNo();
            this.paymentNo = paymentNo;
            this.paymentMethod = paymentMethod;
            this.amount = order.getTotalAmount();
            this.alreadyPaid = alreadyPaid;
            this.mode = mode;
            this.message = message;
        }

        public static PaymentInitiation alreadyPaid(Order order) {
            return new PaymentInitiation(order, order.getPaymentNo(), order.getPaymentMethod(), true, "paid", "订单已支付");
        }

        public static PaymentInitiation of(Order order, String paymentNo, String paymentMethod) {
            String mode = "MOCK".equals(paymentMethod) ? "mock" : "production";
            String msg = "MOCK".equals(paymentMethod) ? "模拟支付模式，请确认支付" : "请前往第三方支付";
            return new PaymentInitiation(order, paymentNo, paymentMethod, false, mode, msg);
        }
    }
}
