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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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

    @Mock
    private PaymentLogRepository paymentLogRepository;

    @InjectMocks
    private OrderService orderService;

    private OrderRequest orderRequest;
    private Book testBook;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(orderService, "orderTimeoutMinutes", 15);

        orderRequest = new OrderRequest();
        orderRequest.setAddress("测试地址");
        orderRequest.setPhone("13800138000");
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
        testBook.setStatus(1);
    }

    private Order createPendingOrder(Long id, Long userId) {
        Order order = new Order();
        order.setId(id);
        order.setOrderNo("TEST" + id);
        order.setUserId(userId);
        order.setStatus(OrderStatus.PENDING);
        order.setTotalAmount(new BigDecimal("29.90"));
        order.setExpireTime(LocalDateTime.now().plusMinutes(15));
        return order;
    }

    private Order createPayingOrder(Long id, Long userId) {
        Order order = createPendingOrder(id, userId);
        order.setStatus(OrderStatus.PAYING);
        return order;
    }

    private Order createPaidOrder(Long id, Long userId) {
        Order order = createPendingOrder(id, userId);
        order.setStatus(OrderStatus.PAID);
        order.setPaymentNo("PAY_TEST");
        order.setPaymentMethod("MOCK");
        order.setPayTime(LocalDateTime.now());
        return order;
    }

    private Order createShippedOrder(Long id, Long userId) {
        Order order = createPaidOrder(id, userId);
        order.setStatus(OrderStatus.SHIPPED);
        order.setTrackingNo("SF123456");
        return order;
    }

    private PaymentLog createSuccessPaymentLog(String paymentNo, Long orderId) {
        PaymentLog log = new PaymentLog();
        log.setId(1L);
        log.setOrderId(orderId);
        log.setOrderNo("TEST" + orderId);
        log.setPaymentNo(paymentNo);
        log.setMethod("MOCK");
        log.setAmount(new BigDecimal("29.90"));
        log.setStatus("SUCCESS");
        return log;
    }

    private PaymentLog createInitiatedPaymentLog(String paymentNo, Long orderId) {
        PaymentLog log = new PaymentLog();
        log.setId(1L);
        log.setOrderId(orderId);
        log.setOrderNo("TEST" + orderId);
        log.setPaymentNo(paymentNo);
        log.setMethod("MOCK");
        log.setAmount(new BigDecimal("29.90"));
        log.setStatus("INITIATED");
        return log;
    }

    @Test
    void testCreateOrder_Success() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));
        when(bookRepository.decrementStock(1L, 1)).thenReturn(1);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId(1L);
            return o;
        });

        Order result = orderService.createOrder(orderRequest, 1L);

        assertNotNull(result);
        assertEquals(OrderStatus.PENDING, result.getStatus());
        assertEquals(new BigDecimal("29.90"), result.getTotalAmount());
        assertNotNull(result.getExpireTime());

        verify(bookRepository, times(1)).decrementStock(1L, 1);
        verify(cartItemRepository, times(1)).deleteByUserId(1L);
    }

    @Test
    void testCreateOrder_InsufficientStock() {
        testBook.setStock(0);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));
        when(bookRepository.decrementStock(1L, 1)).thenReturn(0);

        assertThrows(IllegalArgumentException.class, () -> {
            orderService.createOrder(orderRequest, 1L);
        });

        verify(orderRepository, never()).save(any());
    }

    @Test
    void testCancelOrder_Success_RestockInventory() {
        Order pendingOrder = createPendingOrder(1L, 1L);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));
        when(orderRepository.compareAndSetStatus(eq(1L), eq(OrderStatus.PENDING), eq(OrderStatus.CANCELLED), any()))
                .thenReturn(1);

        OrderItem orderItem = new OrderItem();
        orderItem.setBookId(1L);
        orderItem.setQuantity(1);
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of(orderItem));
        when(bookRepository.incrementStock(1L, 1)).thenReturn(1);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));

        Order result = orderService.cancelOrder(1L, 1L);

        assertNotNull(result);
        verify(bookRepository, times(1)).incrementStock(1L, 1);
    }

    @Test
    void testCancelOrder_AlreadyPaid_ShouldFail() {
        Order paidOrder = createPaidOrder(1L, 1L);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(paidOrder));

        assertThrows(IllegalArgumentException.class, () -> {
            orderService.cancelOrder(1L, 1L);
        });

        verify(bookRepository, never()).incrementStock(anyLong(), anyInt());
    }

    @Test
    void testMockPay_Success() {
        Order pendingOrder = createPendingOrder(1L, 1L);
        Order payingOrder = createPayingOrder(1L, 1L);
        Order paidOrder = createPaidOrder(1L, 1L);

        when(orderRepository.findById(1L))
                .thenReturn(Optional.of(pendingOrder))
                .thenReturn(Optional.of(pendingOrder))
                .thenReturn(Optional.of(payingOrder))
                .thenReturn(Optional.of(payingOrder))
                .thenReturn(Optional.of(paidOrder));
        when(orderRepository.compareAndSetStatus(eq(1L), eq(OrderStatus.PENDING), eq(OrderStatus.PAYING), any()))
                .thenReturn(1);
        when(paymentLogRepository.findByOrderIdAndStatus(eq(1L), eq("INITIATED"))).thenReturn(null).thenReturn(null);
        when(paymentLogRepository.findByPaymentNo(anyString())).thenReturn(Optional.empty());
        when(paymentLogRepository.save(any(PaymentLog.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.markAsPaid(eq(1L), eq(OrderStatus.PENDING), eq(OrderStatus.PAID), anyString(), eq("MOCK"), any()))
                .thenReturn(0);
        when(orderRepository.markAsPaid(eq(1L), eq(OrderStatus.PAYING), eq(OrderStatus.PAID), anyString(), eq("MOCK"), any()))
                .thenReturn(1);

        Order result = orderService.mockPay(1L, 1L);

        assertEquals(OrderStatus.PAID, result.getStatus());
        verify(paymentLogRepository, atLeastOnce()).save(any(PaymentLog.class));
    }

    @Test
    void testMockPay_Idempotent_AlreadyPaid() {
        Order paidOrder = createPaidOrder(1L, 1L);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(paidOrder));

        Order result = orderService.mockPay(1L, 1L);

        assertEquals(OrderStatus.PAID, result.getStatus());
        verify(orderRepository, never()).compareAndSetStatus(anyLong(), anyString(), anyString(), any());
        verify(orderRepository, never()).markAsPaid(anyLong(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void testConfirmPayment_AlreadyPaid_PaymentLogExists_Idempotent() {
        Order paidOrder = createPaidOrder(1L, 1L);
        PaymentLog successLog = createSuccessPaymentLog("PAY_TEST123", 1L);

        when(paymentLogRepository.findByPaymentNo("PAY_TEST123")).thenReturn(Optional.of(successLog));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(paidOrder));

        Order result = orderService.confirmPayment(1L, "PAY_TEST123", "WECHAT", "WX_NOTIFY_001");

        assertEquals(OrderStatus.PAID, result.getStatus());
        verify(orderRepository, never()).markAsPaid(anyLong(), anyString(), anyString(), anyString(), anyString(), any());
        verify(paymentLogRepository, never()).save(any(PaymentLog.class));
    }

    @Test
    void testConfirmPayment_CAS_FirstWinsSecondLoses_Concurrent() {
        String paymentNo = "PAY_CONCURRENT";
        Order pendingOrder = createPendingOrder(1L, 1L);
        Order paidOrder = createPaidOrder(1L, 1L);
        paidOrder.setPaymentNo(paymentNo);
        PaymentLog successLog = createSuccessPaymentLog(paymentNo, 1L);

        when(paymentLogRepository.findByPaymentNo(paymentNo))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(successLog));
        when(orderRepository.findById(1L))
                .thenReturn(Optional.of(pendingOrder))
                .thenReturn(Optional.of(paidOrder));
        when(orderRepository.markAsPaid(eq(1L), eq(OrderStatus.PENDING), eq(OrderStatus.PAID), eq(paymentNo), eq("WECHAT"), any()))
                .thenReturn(0);
        when(orderRepository.markAsPaid(eq(1L), eq(OrderStatus.PAYING), eq(OrderStatus.PAID), eq(paymentNo), eq("WECHAT"), any()))
                .thenReturn(0);

        Order result = orderService.confirmPayment(1L, paymentNo, "WECHAT", "WX_001");

        assertEquals(OrderStatus.PAID, result.getStatus());
        verify(paymentLogRepository, never()).save(any(PaymentLog.class));
    }

    @Test
    void testConfirmPayment_DuplicateCallback_AlreadySuccess() {
        String paymentNo = "PAY_DUPLICATE_CALLBACK";
        PaymentLog successLog = createSuccessPaymentLog(paymentNo, 1L);
        Order paidOrder = createPaidOrder(1L, 1L);
        paidOrder.setPaymentNo(paymentNo);

        when(paymentLogRepository.findByPaymentNo(paymentNo)).thenReturn(Optional.of(successLog));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(paidOrder));

        Order result = orderService.confirmPayment(1L, paymentNo, "ALIPAY", "ALI_001");

        assertEquals(OrderStatus.PAID, result.getStatus());
        verify(orderRepository, never()).markAsPaid(anyLong(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void testShipOrder_OnlyFromPaidStatus() {
        Order paidOrder = createPaidOrder(1L, 1L);
        Order shippedOrder = createShippedOrder(1L, 1L);

        when(orderRepository.findById(1L))
                .thenReturn(Optional.of(paidOrder))
                .thenReturn(Optional.of(shippedOrder));
        when(orderRepository.markAsShipped(eq(1L), eq(OrderStatus.PAID), eq(OrderStatus.SHIPPED), eq("SF123"), any()))
                .thenReturn(1);

        Order result = orderService.shipOrder(1L, "SF123");
        assertEquals(OrderStatus.SHIPPED, result.getStatus());
    }

    @Test
    void testShipOrder_FromPending_ShouldFail() {
        Order pendingOrder = createPendingOrder(1L, 1L);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));

        assertThrows(IllegalArgumentException.class, () -> {
            orderService.shipOrder(1L, "SF123");
        });
    }

    @Test
    void testCompleteOrder_OnlyFromShippedStatus() {
        Order shippedOrder = createShippedOrder(1L, 1L);
        Order completedOrder = createShippedOrder(1L, 1L);
        completedOrder.setStatus(OrderStatus.COMPLETED);

        when(orderRepository.findById(1L))
                .thenReturn(Optional.of(shippedOrder))
                .thenReturn(Optional.of(completedOrder));
        when(orderRepository.compareAndSetStatus(eq(1L), eq(OrderStatus.SHIPPED), eq(OrderStatus.COMPLETED), any()))
                .thenReturn(1);

        Order result = orderService.completeOrder(1L, 1L);
        assertEquals(OrderStatus.COMPLETED, result.getStatus());
    }

    @Test
    void testAdminUpdateOrderStatus_InvalidTransition_Rejected() {
        Order pendingOrder = createPendingOrder(1L, 1L);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingOrder));

        assertThrows(IllegalArgumentException.class, () -> {
            orderService.adminUpdateOrderStatus(1L, OrderStatus.COMPLETED, null);
        });

        verify(orderRepository, never()).compareAndSetStatus(anyLong(), anyString(), anyString(), any());
    }

    @Test
    void testCreateOrder_BookNotActive_ShouldFail() {
        testBook.setStatus(0);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));

        assertThrows(IllegalArgumentException.class, () -> {
            orderService.createOrder(orderRequest, 1L);
        });

        verify(bookRepository, never()).decrementStock(anyLong(), anyInt());
    }

    @Test
    void testConfirmPayment_Success_SavesPaymentLog() {
        String paymentNo = "PAY_NEW";
        Order pendingOrder = createPendingOrder(1L, 1L);
        Order paidOrder = createPaidOrder(1L, 1L);
        paidOrder.setPaymentNo(paymentNo);

        when(paymentLogRepository.findByPaymentNo(paymentNo)).thenReturn(Optional.empty());
        when(paymentLogRepository.findByOrderIdAndStatus(eq(1L), eq("INITIATED"))).thenReturn(null);
        when(paymentLogRepository.save(any(PaymentLog.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.findById(1L))
                .thenReturn(Optional.of(pendingOrder))
                .thenReturn(Optional.of(paidOrder));
        when(orderRepository.markAsPaid(eq(1L), eq(OrderStatus.PENDING), eq(OrderStatus.PAID), eq(paymentNo), eq("WECHAT"), any()))
                .thenReturn(1);

        Order result = orderService.confirmPayment(1L, paymentNo, "WECHAT", "WX_NEW");

        assertEquals(OrderStatus.PAID, result.getStatus());
        verify(paymentLogRepository, times(1)).save(argThat(log ->
                "SUCCESS".equals(log.getStatus()) && paymentNo.equals(log.getPaymentNo())
        ));
    }

    @Test
    void testStatusTransitions_CancelledOrderCannotBePaid() {
        Order cancelledOrder = createPendingOrder(1L, 1L);
        cancelledOrder.setStatus(OrderStatus.CANCELLED);
        when(paymentLogRepository.findByPaymentNo("PAY_TEST")).thenReturn(Optional.empty());
        when(orderRepository.findById(1L)).thenReturn(Optional.of(cancelledOrder));

        assertThrows(IllegalArgumentException.class, () -> {
            orderService.confirmPayment(1L, "PAY_TEST", "MOCK", null);
        });
    }

    @Test
    void testInitiatePayment_AlreadyPaid_ReturnsSuccess() {
        Order paidOrder = createPaidOrder(1L, 1L);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(paidOrder));

        OrderService.PaymentInitiation result = orderService.initiatePayment(1L, "MOCK", 1L);

        assertTrue(result.alreadyPaid);
        verify(orderRepository, never()).compareAndSetStatus(anyLong(), anyString(), anyString(), any());
    }

    @Test
    void testConfirmPaymentByPaymentNo_WechatCallback() {
        String paymentNo = "PAY_WX_CALLBACK";
        PaymentLog initiatedLog = createInitiatedPaymentLog(paymentNo, 1L);
        Order payingOrder = createPayingOrder(1L, 1L);
        Order paidOrder = createPaidOrder(1L, 1L);
        paidOrder.setPaymentNo(paymentNo);

        when(paymentLogRepository.findByPaymentNo(paymentNo)).thenReturn(Optional.of(initiatedLog));
        when(orderRepository.findById(1L))
                .thenReturn(Optional.of(payingOrder))
                .thenReturn(Optional.of(paidOrder));
        when(paymentLogRepository.save(any(PaymentLog.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.markAsPaid(eq(1L), eq(OrderStatus.PENDING), eq(OrderStatus.PAID), eq(paymentNo), eq("WECHAT"), any()))
                .thenReturn(0);
        when(orderRepository.markAsPaid(eq(1L), eq(OrderStatus.PAYING), eq(OrderStatus.PAID), eq(paymentNo), eq("WECHAT"), any()))
                .thenReturn(1);

        Order result = orderService.confirmPaymentByPaymentNo(paymentNo, "WECHAT", "4200001234567890");

        assertEquals(OrderStatus.PAID, result.getStatus());
        verify(paymentLogRepository, times(1)).save(argThat(log ->
                "SUCCESS".equals(log.getStatus()) && "4200001234567890".equals(log.getThirdPartyNo())
        ));
    }
}
