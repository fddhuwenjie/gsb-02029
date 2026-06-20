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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    
    @Mock
    private OrderRepository orderRepository;
    
    @Mock
    private OrderItemRepository orderItemRepository;
    
    @Mock
    private BookRepository bookRepository;
    
    @Mock
    private CartItemRepository cartItemRepository;
    
    @InjectMocks
    private OrderService orderService;
    
    private OrderRequest orderRequest;
    private Book testBook;
    private List<OrderItem> orderItems;
    
    @BeforeEach
    void setUp() {
        orderRequest = new OrderRequest();
        orderRequest.setAddress("测试地址");
        orderRequest.setPhone("138******00");
        orderRequest.setReceiver("测试用户");
        
        OrderRequest.OrderItemRequest itemRequest = new OrderRequest.OrderItemRequest();
        itemRequest.setBookId(1L);
        itemRequest.setQuantity(1);
        orderRequest.setItems(List.of(itemRequest));
        
        testBook = new Book();
        testBook.setId(1L);
        testBook.setTitle("测试书籍");
        testBook.setPrice(new BigDecimal("29.90"));
        testBook.setStock(10);
        
        OrderItem orderItem = new OrderItem();
        orderItem.setId(1L);
        orderItem.setOrderId(1L);
        orderItem.setBookId(1L);
        orderItem.setQuantity(1);
        orderItem.setPrice(new BigDecimal("29.90"));
        orderItems = List.of(orderItem);
    }

    private Order createOrder(Long id, String status) {
        Order order = new Order();
        order.setId(id);
        order.setOrderNo("TEST" + id);
        order.setUserId(1L);
        order.setStatus(status);
        order.setTotalAmount(new BigDecimal("29.90"));
        if (OrderStatus.PENDING.equals(status)) {
            order.setExpireTime(LocalDateTime.now().plusMinutes(30));
        }
        return order;
    }
    
    @Test
    void testCreateOrder_ShouldNotDeductStock() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(1L);
            return order;
        });
        
        Order result = orderService.createOrder(orderRequest, 1L);
        
        assertNotNull(result);
        assertEquals(OrderStatus.PENDING, result.getStatus());
        assertNotNull(result.getExpireTime());
        
        verify(bookRepository, never()).decrementStock(anyLong(), anyInt());
        verify(orderRepository, times(1)).save(any(Order.class));
    }
    
    @Test
    void testPayOrder_Success_ShouldDeductStockAndMarkPaid() {
        Order pendingOrder = createOrder(1L, OrderStatus.PENDING);
        Order paidOrder = createOrder(1L, OrderStatus.PAID);
        
        when(orderRepository.findById(eq(1L)))
                .thenReturn(Optional.of(pendingOrder))
                .thenReturn(Optional.of(paidOrder));
        when(orderItemRepository.findByOrderId(1L)).thenReturn(orderItems);
        when(bookRepository.decrementStock(1L, 1)).thenReturn(1);
        when(orderRepository.markAsPaid(eq(1L), eq(OrderStatus.PENDING), eq(OrderStatus.PAID),
                any(), any(), any(), any())).thenReturn(1);
        
        Order result = orderService.payOrder(1L, "PAY123456", "MOCK");
        
        assertNotNull(result);
        assertEquals(OrderStatus.PAID, result.getStatus());
        
        verify(bookRepository, times(1)).decrementStock(1L, 1);
        verify(orderRepository, times(1)).markAsPaid(eq(1L), eq(OrderStatus.PENDING), eq(OrderStatus.PAID),
                any(), any(), any(), any());
    }
    
    @Test
    void testPayOrder_DuplicatePayment_Idempotent() {
        Order paidOrder = createOrder(1L, OrderStatus.PAID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(paidOrder));
        
        Order result = orderService.payOrder(1L, "PAY123456", "MOCK");
        
        assertNotNull(result);
        assertEquals(OrderStatus.PAID, result.getStatus());
        
        verify(bookRepository, never()).decrementStock(anyLong(), anyInt());
        verify(orderRepository, never()).markAsPaid(anyLong(), anyString(), anyString(),
                any(), any(), any(), any());
    }
    
    @Test
    void testPayOrder_ConcurrentDuplicatePayment_OnlyOneSucceeds() {
        Order pendingOrder = createOrder(1L, OrderStatus.PENDING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));
        when(orderItemRepository.findByOrderId(1L)).thenReturn(orderItems);
        when(bookRepository.decrementStock(1L, 1)).thenReturn(1);
        when(orderRepository.markAsPaid(eq(1L), eq(OrderStatus.PENDING), eq(OrderStatus.PAID),
                any(), any(), any(), any())).thenReturn(0);
        
        assertThrows(IllegalStateException.class, () -> {
            orderService.payOrder(1L, "PAY123456", "MOCK");
        });
        
        verify(bookRepository, times(1)).incrementStock(1L, 1);
    }
    
    @Test
    void testPayOrder_AlreadyShipped_Idempotent() {
        Order shippedOrder = createOrder(1L, OrderStatus.SHIPPED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(shippedOrder));
        
        Order result = orderService.payOrder(1L, "PAY123456", "MOCK");
        
        assertEquals(OrderStatus.SHIPPED, result.getStatus());
        verify(bookRepository, never()).decrementStock(anyLong(), anyInt());
    }
    
    @Test
    void testPayOrder_AlreadyCompleted_Idempotent() {
        Order completedOrder = createOrder(1L, OrderStatus.COMPLETED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(completedOrder));
        
        Order result = orderService.payOrder(1L, "PAY123456", "MOCK");
        
        assertEquals(OrderStatus.COMPLETED, result.getStatus());
        verify(bookRepository, never()).decrementStock(anyLong(), anyInt());
    }
    
    @Test
    void testPayOrder_ExpiredOrder_ShouldThrowAndCancel() {
        Order expiredOrder = createOrder(1L, OrderStatus.PENDING);
        expiredOrder.setExpireTime(LocalDateTime.now().minusMinutes(1));
        
        when(orderRepository.findById(1L)).thenReturn(Optional.of(expiredOrder));
        when(orderRepository.cancelOrder(eq(1L), eq(OrderStatus.PENDING), eq(OrderStatus.CANCELLED),
                any(), any())).thenReturn(1);
        
        assertThrows(IllegalStateException.class, () -> {
            orderService.payOrder(1L, "PAY123456", "MOCK");
        });
    }
    
    @Test
    void testPayOrder_InsufficientStock_ShouldThrow() {
        Order pendingOrder = createOrder(1L, OrderStatus.PENDING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));
        when(orderItemRepository.findByOrderId(1L)).thenReturn(orderItems);
        when(bookRepository.decrementStock(1L, 1)).thenReturn(0);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));
        
        assertThrows(IllegalStateException.class, () -> {
            orderService.payOrder(1L, "PAY123456", "MOCK");
        });
        
        verify(orderRepository, never()).markAsPaid(anyLong(), anyString(), anyString(),
                any(), any(), any(), any());
    }
    
    @Test
    void testCancelOrder_Success() {
        Order pendingOrder = createOrder(1L, OrderStatus.PENDING);
        Order cancelledOrder = createOrder(1L, OrderStatus.CANCELLED);
        
        when(orderRepository.findById(eq(1L)))
                .thenReturn(Optional.of(pendingOrder))
                .thenReturn(Optional.of(cancelledOrder));
        when(orderRepository.cancelOrder(eq(1L), eq(OrderStatus.PENDING), eq(OrderStatus.CANCELLED),
                any(), any())).thenReturn(1);
        
        Order result = orderService.cancelOrder(1L);
        
        assertNotNull(result);
        verify(orderRepository, times(1)).cancelOrder(eq(1L), eq(OrderStatus.PENDING), eq(OrderStatus.CANCELLED),
                any(), any());
    }
    
    @Test
    void testCancelOrder_AlreadyCancelled_Idempotent() {
        Order cancelledOrder = createOrder(1L, OrderStatus.CANCELLED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(cancelledOrder));
        
        Order result = orderService.cancelOrder(1L);
        
        assertEquals(OrderStatus.CANCELLED, result.getStatus());
        verify(orderRepository, never()).cancelOrder(anyLong(), anyString(), anyString(), any(), any());
    }
    
    @Test
    void testCancelOrder_PaidOrder_ShouldThrow() {
        Order paidOrder = createOrder(1L, OrderStatus.PAID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(paidOrder));
        
        assertThrows(IllegalStateException.class, () -> {
            orderService.cancelOrder(1L);
        });
    }
    
    @Test
    void testShipOrder_Success() {
        Order paidOrder = createOrder(1L, OrderStatus.PAID);
        Order shippedOrder = createOrder(1L, OrderStatus.SHIPPED);
        
        when(orderRepository.findById(eq(1L)))
                .thenReturn(Optional.of(paidOrder))
                .thenReturn(Optional.of(shippedOrder));
        when(orderRepository.markAsShipped(eq(1L), eq(OrderStatus.PAID), eq(OrderStatus.SHIPPED),
                any(), any(), eq("SF123456789"))).thenReturn(1);
        
        Order result = orderService.shipOrder(1L, "SF123456789");
        
        assertEquals(OrderStatus.SHIPPED, result.getStatus());
    }
    
    @Test
    void testShipOrder_PendingOrder_ShouldThrow() {
        Order pendingOrder = createOrder(1L, OrderStatus.PENDING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));
        
        assertThrows(IllegalStateException.class, () -> {
            orderService.shipOrder(1L, "SF123456789");
        });
    }
    
    @Test
    void testCompleteOrder_Success() {
        Order shippedOrder = createOrder(1L, OrderStatus.SHIPPED);
        Order completedOrder = createOrder(1L, OrderStatus.COMPLETED);
        
        when(orderRepository.findById(eq(1L)))
                .thenReturn(Optional.of(shippedOrder))
                .thenReturn(Optional.of(completedOrder));
        when(orderRepository.markAsCompleted(eq(1L), eq(OrderStatus.SHIPPED), eq(OrderStatus.COMPLETED),
                any(), any())).thenReturn(1);
        
        Order result = orderService.completeOrder(1L);
        
        assertEquals(OrderStatus.COMPLETED, result.getStatus());
    }
    
    @Test
    void testCompleteOrder_PaidOrder_ShouldThrow() {
        Order paidOrder = createOrder(1L, OrderStatus.PAID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(paidOrder));
        
        assertThrows(IllegalStateException.class, () -> {
            orderService.completeOrder(1L);
        });
    }
    
    @Test
    void testPayOrder_CancelledOrder_ShouldThrow() {
        Order cancelledOrder = createOrder(1L, OrderStatus.CANCELLED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(cancelledOrder));
        
        assertThrows(IllegalStateException.class, () -> {
            orderService.payOrder(1L, "PAY123456", "MOCK");
        });
    }
    
    @Test
    void testDuplicatePaymentCallbacks_MultipleCalls_OnlyOneStockDeduction() {
        Order pendingOrder = createOrder(1L, OrderStatus.PENDING);
        Order paidOrder = createOrder(1L, OrderStatus.PAID);
        
        when(orderRepository.findById(eq(1L)))
                .thenReturn(Optional.of(pendingOrder))
                .thenReturn(Optional.of(paidOrder));
        when(orderItemRepository.findByOrderId(1L)).thenReturn(orderItems);
        when(bookRepository.decrementStock(1L, 1)).thenReturn(1);
        when(orderRepository.markAsPaid(eq(1L), eq(OrderStatus.PENDING), eq(OrderStatus.PAID),
                any(), any(), any(), any())).thenReturn(1);
        
        Order firstResult = orderService.payOrder(1L, "PAY123", "WECHAT");
        Order secondResult = orderService.payOrder(1L, "PAY123", "WECHAT");
        
        assertEquals(OrderStatus.PAID, firstResult.getStatus());
        assertEquals(OrderStatus.PAID, secondResult.getStatus());
        
        verify(bookRepository, times(1)).decrementStock(1L, 1);
    }
}
