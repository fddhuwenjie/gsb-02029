package com.bookstore.service;

import com.bookstore.dto.OrderRequest;
import com.bookstore.entity.Book;
import com.bookstore.entity.Order;
import com.bookstore.repository.BookRepository;
import com.bookstore.repository.OrderItemRepository;
import com.bookstore.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    
    @Mock
    private OrderRepository orderRepository;
    
    @Mock
    private OrderItemRepository orderItemRepository;
    
    @Mock
    private BookRepository bookRepository;
    
    @InjectMocks
    private OrderService orderService;
    
    private OrderRequest orderRequest;
    private Book testBook;
    
    @BeforeEach
    void setUp() {
        // 准备测试数据
        orderRequest = new OrderRequest();
        orderRequest.setAddress("测试地址");
        orderRequest.setPhone("13800138000");
        orderRequest.setReceiver("测试用户");
        
        OrderRequest.OrderItemRequest itemRequest = new OrderRequest.OrderItemRequest();
        itemRequest.setBookId(1L);
        itemRequest.setQuantity(2);
        orderRequest.setItems(java.util.List.of(itemRequest));
        
        testBook = new Book();
        testBook.setId(1L);
        testBook.setTitle("测试书籍");
        testBook.setPrice(new BigDecimal("29.90"));
        testBook.setStock(10);
    }
    
    @Test
    void testCreateOrder_Success() {
        // Mock库存充足
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(1L);
            return order;
        });
        
        // 执行创建订单
        Order result = orderService.createOrder(orderRequest, 1L);
        
        // 验证结果
        assertNotNull(result);
        assertEquals("测试地址", result.getAddress());
        assertEquals("13800138000", result.getPhone());
        assertEquals("PENDING", result.getStatus());
        
        // 验证库存扣减
        assertEquals(8, testBook.getStock());
        
        // 验证调用次数
        verify(bookRepository, times(2)).findById(1L); // 1次计算价格 + 1次扣库存
        verify(orderRepository, times(1)).save(any(Order.class));
        verify(orderItemRepository, times(1)).save(any());
    }
    
    @Test
    void testCreateOrder_InsufficientStock() {
        // Mock库存不足
        testBook.setStock(1);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));
        
        // 验证抛出异常
        assertThrows(IllegalArgumentException.class, () -> {
            orderService.createOrder(orderRequest, 1L);
        });
        
        // 验证订单未保存
        verify(orderRepository, never()).save(any());
    }
    
    @Test
    void testCreateOrder_BookNotFound() {
        // Mock书籍不存在
        when(bookRepository.findById(1L)).thenReturn(Optional.empty());
        
        // 验证抛出异常
        assertThrows(IllegalArgumentException.class, () -> {
            orderService.createOrder(orderRequest, 1L);
        });
    }
    
    @Test
    void testCancelOrder_Success() {
        // Mock订单存在
        Order order = new Order();
        order.setId(1L);
        order.setStatus("PENDING");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        
        // 执行取消订单
        Order result = orderService.cancelOrder(1L);
        
        // 验证结果
        assertEquals("CANCELLED", result.getStatus());
        verify(orderRepository, times(1)).save(order);
    }
    
    @Test
    void testCancelOrder_NotFound() {
        // Mock订单不存在
        when(orderRepository.findById(1L)).thenReturn(Optional.empty());
        
        // 验证抛出异常
        assertThrows(IllegalArgumentException.class, () -> {
            orderService.cancelOrder(1L);
        });
    }
}
