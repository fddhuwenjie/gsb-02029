package com.bookstore.service;

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
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
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
    private Order pendingOrder;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(orderService, "orderExpireMinutes", 30);

        orderRequest = new OrderRequest();
        orderRequest.setAddress("测试地址");
        orderRequest.setPhone("13800138000");
        orderRequest.setReceiver("测试用户");

        OrderRequest.OrderItemRequest itemRequest = new OrderRequest.OrderItemRequest();
        itemRequest.setBookId(1L);
        itemRequest.setQuantity(2);
        orderRequest.setItems(List.of(itemRequest));

        testBook = new Book();
        testBook.setId(1L);
        testBook.setTitle("测试书籍");
        testBook.setPrice(new BigDecimal("29.90"));
        testBook.setStock(10);

        pendingOrder = new Order();
        pendingOrder.setId(1L);
        pendingOrder.setOrderNo("TEST20240101001");
        pendingOrder.setUserId(1L);
        pendingOrder.setStatus("PENDING");
        pendingOrder.setTotalAmount(new BigDecimal("59.80"));
    }

    private Order makeOrder(Long id, String status) {
        Order o = new Order();
        o.setId(id);
        o.setOrderNo("NO" + id);
        o.setUserId(1L);
        o.setStatus(status);
        o.setTotalAmount(new BigDecimal("100.00"));
        return o;
    }

    // ==================== 创建订单 ====================

    @Test
    void createOrder_shouldReserveStock_andSetExpireAt() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));
        when(bookRepository.decrementStock(1L, 2)).thenReturn(1);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(100L);
            return o;
        });
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = orderService.createOrder(orderRequest, 1L);

        assertNotNull(result);
        assertEquals("PENDING", result.getStatus());
        assertNotNull(result.getExpireAt());
        assertTrue(result.getExpireAt().isAfter(LocalDateTime.now().plusMinutes(29)));

        verify(bookRepository, times(1)).decrementStock(1L, 2);
        verify(cartItemRepository, times(1)).deleteByUserId(1L);
    }

    @Test
    void createOrder_insufficientStock_shouldThrow_andNotSaveOrder() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));
        when(bookRepository.decrementStock(1L, 2)).thenReturn(0);

        assertThrows(IllegalArgumentException.class, () -> orderService.createOrder(orderRequest, 1L));
        verify(orderRepository, never()).save(any());
        verify(cartItemRepository, never()).deleteByUserId(any());
    }

    // ==================== 支付幂等：重复点击支付 ====================

    @Test
    void markPaid_firstTime_shouldTransitionToPaid() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));
        when(orderRepository.casMarkPaid(eq(1L), eq("PAY123"), eq("WECHAT"), any())).thenReturn(1);

        Order result = orderService.markPaid(1L, "PAY123", "WECHAT");

        assertEquals("PAID", result.getStatus());
        assertEquals("PAY123", result.getPaymentId());
        verify(orderRepository, times(1)).casMarkPaid(eq(1L), eq("PAY123"), eq("WECHAT"), any());
    }

    @Test
    void markPaid_whenAlreadyPaid_shouldBeIdempotent_noSecondCasUpdate() {
        Order paidOrder = makeOrder(1L, "PAID");
        paidOrder.setPaymentId("PAY123");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(paidOrder));

        Order result = orderService.markPaid(1L, "PAY123", "WECHAT");

        assertEquals("PAID", result.getStatus());
        verify(orderRepository, never()).casMarkPaid(anyLong(), any(), any(), any());
    }

    @Test
    void markPaid_doubleClick_concurrentRace_shouldOnlySucceedOnce() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));
        when(orderRepository.casMarkPaid(eq(1L), eq("PAY_A"), eq("WECHAT"), any())).thenReturn(0);
        Order paidByOther = makeOrder(1L, "PAID");
        paidByOther.setPaymentId("PAY_B");
        when(orderRepository.findById(1L)).thenReturn(
                Optional.of(pendingOrder),
                Optional.of(paidByOther)
        );

        Order result = orderService.markPaid(1L, "PAY_A", "WECHAT");

        assertEquals("PAID", result.getStatus());
        verify(orderRepository, times(1)).casMarkPaid(anyLong(), any(), any(), any());
    }

    // ==================== 支付回调重试 ====================

    @Test
    void markPaid_callbackRetry_whenAlreadyShipped_shouldBeIdempotent() {
        Order shippedOrder = makeOrder(1L, "SHIPPED");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(shippedOrder));

        Order result = orderService.markPaid(1L, "WX_TXN_001", "WECHAT");

        assertEquals("SHIPPED", result.getStatus());
        verify(orderRepository, never()).casMarkPaid(anyLong(), any(), any(), any());
    }

    @Test
    void markPaid_callbackRetry_whenAlreadyCompleted_shouldBeIdempotent() {
        Order completedOrder = makeOrder(1L, "COMPLETED");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(completedOrder));

        Order result = orderService.markPaid(1L, "WX_TXN_001", "WECHAT");

        assertEquals("COMPLETED", result.getStatus());
        verify(orderRepository, never()).casMarkPaid(anyLong(), any(), any(), any());
    }

    // ==================== 取消订单后支付到达 ====================

    @Test
    void markPaid_whenOrderCancelled_shouldRejectPayment() {
        Order cancelledOrder = makeOrder(1L, "CANCELLED");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(cancelledOrder));

        assertThrows(IllegalStateException.class, () -> orderService.markPaid(1L, "PAY123", "WECHAT"));
        verify(orderRepository, never()).casMarkPaid(anyLong(), any(), any(), any());
    }

    // ==================== 取消订单 + 库存恢复 ====================

    @Test
    void cancelOrder_pendingOrder_shouldCancelAndRestoreStock() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));
        when(orderRepository.casCancel(eq(1L), any(), eq("用户主动取消"))).thenReturn(1);

        OrderItem oi = new OrderItem();
        oi.setBookId(1L);
        oi.setQuantity(2);
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of(oi));
        when(bookRepository.incrementStock(1L, 2)).thenReturn(1);

        Order result = orderService.cancelOrder(1L, "用户主动取消");

        assertEquals("CANCELLED", result.getStatus());
        verify(bookRepository, times(1)).incrementStock(1L, 2);
    }

    @Test
    void cancelOrder_paidOrder_shouldCancelAndRestoreStock() {
        Order paidOrder = makeOrder(1L, "PAID");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(paidOrder));
        when(orderRepository.casCancel(eq(1L), any(), eq("退款"))).thenReturn(1);

        OrderItem oi = new OrderItem();
        oi.setBookId(1L);
        oi.setQuantity(1);
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of(oi));
        when(bookRepository.incrementStock(1L, 1)).thenReturn(1);

        Order result = orderService.cancelOrder(1L, "退款");

        assertEquals("CANCELLED", result.getStatus());
        verify(bookRepository, times(1)).incrementStock(1L, 1);
    }

    @Test
    void cancelOrder_alreadyCancelled_shouldBeIdempotent() {
        Order cancelledOrder = makeOrder(1L, "CANCELLED");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(cancelledOrder));

        Order result = orderService.cancelOrder(1L, "重复取消");

        assertEquals("CANCELLED", result.getStatus());
        verify(orderRepository, never()).casCancel(anyLong(), any(), any());
        verify(bookRepository, never()).incrementStock(anyLong(), anyInt());
    }

    @Test
    void cancelOrder_shippedOrder_shouldReject() {
        Order shippedOrder = makeOrder(1L, "SHIPPED");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(shippedOrder));

        assertThrows(IllegalStateException.class, () -> orderService.cancelOrder(1L, "test"));
        verify(orderRepository, never()).casCancel(anyLong(), any(), any());
    }

    @Test
    void cancelOrder_completedOrder_shouldReject() {
        Order completedOrder = makeOrder(1L, "COMPLETED");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(completedOrder));

        assertThrows(IllegalStateException.class, () -> orderService.cancelOrder(1L, "test"));
    }

    // ==================== 发货状态机 ====================

    @Test
    void shipOrder_fromPaid_shouldSucceed() {
        Order paidOrder = makeOrder(1L, "PAID");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(paidOrder));
        when(orderRepository.casShip(eq(1L), eq("SF123456"), any())).thenReturn(1);

        Order result = orderService.shipOrder(1L, "SF123456");

        assertEquals("SHIPPED", result.getStatus());
        assertEquals("SF123456", result.getTrackingNo());
    }

    @Test
    void shipOrder_emptyTrackingNo_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> orderService.shipOrder(1L, ""));
        assertThrows(IllegalArgumentException.class, () -> orderService.shipOrder(1L, "  "));
        assertThrows(IllegalArgumentException.class, () -> orderService.shipOrder(1L, null));
    }

    @Test
    void shipOrder_fromPending_shouldReject() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));
        when(orderRepository.casShip(eq(1L), eq("SF123"), any())).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> orderService.shipOrder(1L, "SF123"));
    }

    @Test
    void shipOrder_alreadyShipped_shouldBeIdempotent() {
        Order shipped = makeOrder(1L, "SHIPPED");
        shipped.setTrackingNo("SF123");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(shipped));

        Order result = orderService.shipOrder(1L, "SF123");
        assertEquals("SHIPPED", result.getStatus());
        verify(orderRepository, never()).casShip(anyLong(), any(), any());
    }

    // ==================== 确认收货 ====================

    @Test
    void completeOrder_fromShipped_shouldSucceed() {
        Order shipped = makeOrder(1L, "SHIPPED");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(shipped));
        when(orderRepository.casComplete(eq(1L), any())).thenReturn(1);

        Order result = orderService.completeOrder(1L);

        assertEquals("COMPLETED", result.getStatus());
    }

    @Test
    void completeOrder_fromPaid_shouldReject() {
        Order paid = makeOrder(1L, "PAID");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(paid));
        when(orderRepository.casComplete(eq(1L), any())).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> orderService.completeOrder(1L));
    }

    @Test
    void completeOrder_alreadyCompleted_shouldBeIdempotent() {
        Order completed = makeOrder(1L, "COMPLETED");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(completed));

        Order result = orderService.completeOrder(1L);
        assertEquals("COMPLETED", result.getStatus());
        verify(orderRepository, never()).casComplete(anyLong(), any());
    }

    // ==================== 超时自动取消 ====================

    @Test
    void cancelExpiredOrders_shouldCancelOnlyExpiredPendingOrders_andRestoreStock() {
        Order expired1 = makeOrder(10L, "PENDING");
        Order expired2 = makeOrder(11L, "PENDING");
        when(orderRepository.findExpiredOrders(any())).thenReturn(List.of(expired1, expired2));

        when(orderRepository.findById(10L)).thenReturn(Optional.of(expired1));
        when(orderRepository.casCancel(eq(10L), any(), eq("超时未支付自动取消"))).thenReturn(1);
        when(orderItemRepository.findByOrderId(10L)).thenReturn(Collections.emptyList());

        when(orderRepository.findById(11L)).thenReturn(Optional.of(expired2));
        when(orderRepository.casCancel(eq(11L), any(), eq("超时未支付自动取消"))).thenReturn(1);
        when(orderItemRepository.findByOrderId(11L)).thenReturn(Collections.emptyList());

        int count = orderService.cancelExpiredOrders();

        assertEquals(2, count);
        verify(bookRepository, never()).incrementStock(anyLong(), anyInt());
    }

    // ==================== 库存不重复扣减验证 ====================

    @Test
    void markPaid_shouldNotDeductStockAgain_stockWasReservedAtCreation() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));
        when(orderRepository.casMarkPaid(anyLong(), any(), any(), any())).thenReturn(1);

        orderService.markPaid(1L, "PAY1", "WECHAT");

        verify(bookRepository, never()).decrementStock(anyLong(), anyInt());
    }

    @Test
    void cancelOrder_thenCancelAgain_shouldNotRestoreStockTwice() {
        Order cancelledOrder = makeOrder(1L, "CANCELLED");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(cancelledOrder));

        orderService.cancelOrder(1L, "第二次取消");

        verify(bookRepository, never()).incrementStock(anyLong(), anyInt());
    }
}
