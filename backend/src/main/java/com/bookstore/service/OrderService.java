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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class OrderService {
    
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    
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
    
    public Order cancelOrder(Long id) {
        Order order = orderRepository.findById(id).orElse(null);
        if (order == null) {
            log.warn("取消订单失败，订单 {} 不存在", id);
            throw new IllegalArgumentException("订单不存在");
        }
        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(LocalDateTime.now());
        log.info("订单 {} 已取消", id);
        return orderRepository.save(order);
    }
    
    private String generateOrderNo() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) 
               + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}
