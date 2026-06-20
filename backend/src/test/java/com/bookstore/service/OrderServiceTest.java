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
import org.junit.jupiter.api.Nested;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OrderService 关键并发/幂等场景验证：
 *
 *  1. 创建订单 - 库存原子扣减
 *  2. 库存不足 / 书籍不存在
 *  3. 重复支付（重复点击 / 回调重试）—— paymentNo 命中幂等
 *  4. 不同 paymentNo 但订单已 PAID —— 拒绝重复支付
 *  5. 已取消订单不可支付
 *  6. 用户取消订单 —— 库存归还，且重复取消不会重复归还
 *  7. 并发支付与取消的 CAS 互斥
 *  8. 状态机非法迁移被拒
 */
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

    @BeforeEach
    void setUp() {
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
    }

    // -------------------- 创建订单 --------------------

    @Test
    void testCreateOrder_Success_decrementStockAtomically() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));
        when(bookRepository.decrementStock(1L, 2)).thenReturn(1);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(1L);
            return order;
        });

        Order result = orderService.createOrder(orderRequest, 1L);

        assertNotNull(result);
        assertEquals(OrderStatus.PENDING, result.getStatus());
        verify(bookRepository, times(1)).decrementStock(1L, 2);
        verify(orderItemRepository, times(1)).save(any());
    }

    @Test
    void testCreateOrder_InsufficientStock_throws() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(testBook));
        when(bookRepository.decrementStock(1L, 2)).thenReturn(0); // 模拟原子扣减返回 0 行

        assertThrows(IllegalArgumentException.class,
                () -> orderService.createOrder(orderRequest, 1L));

        verify(orderRepository, never()).save(any());
    }

    @Test
    void testCreateOrder_BookNotFound_throws() {
        when(bookRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> orderService.createOrder(orderRequest, 1L));
        verify(bookRepository, never()).decrementStock(anyLong(), anyInt());
    }

    // -------------------- 支付幂等场景 --------------------

    @Nested
    class PaymentIdempotency {

        @Test
        void firstTimeMarkPaid_succeedsAndPersistsPaymentNo() {
            Order pending = pendingOrder();
            when(orderRepository.findById(10L))
                    .thenReturn(Optional.of(pending))
                    .thenReturn(Optional.of(paidOrder("PAY_001")));
            when(orderRepository.markPaidIfPending(eq(10L), eq("PAY_001"), eq("WECHAT"), any()))
                    .thenReturn(1);

            Order result = orderService.markPaid(10L, "PAY_001", "WECHAT");

            assertEquals(OrderStatus.PAID, result.getStatus());
            assertEquals("PAY_001", result.getPaymentNo());
            verify(orderRepository, times(1))
                    .markPaidIfPending(eq(10L), eq("PAY_001"), eq("WECHAT"), any());
        }

        @Test
        void samePaymentNoTwice_isIdempotent_noDoubleWrite() {
            // 第一次：CAS 成功；第二次：findById 已经看到 PAID，且 paymentNo 一致 —— 早返回
            Order alreadyPaid = paidOrder("PAY_001");
            when(orderRepository.findById(10L)).thenReturn(Optional.of(alreadyPaid));

            Order result = orderService.markPaid(10L, "PAY_001", "WECHAT");

            assertEquals(OrderStatus.PAID, result.getStatus());
            // 关键断言：不会再次写库
            verify(orderRepository, never())
                    .markPaidIfPending(anyLong(), anyString(), anyString(), any());
        }

        @Test
        void duplicateClick_concurrentPaymentRace_secondCallSeesCASZero() {
            // 模拟两次并发请求都通过了状态预检（status==PENDING）但 CAS 只命中一次
            Order pending = pendingOrder();
            Order paid = paidOrder("PAY_001");
            when(orderRepository.findById(10L))
                    .thenReturn(Optional.of(pending))   // 进入 markPaid 时
                    .thenReturn(Optional.of(paid))      // CAS 失败后再读取
                    .thenReturn(Optional.of(paid));     // 兜底
            when(orderRepository.markPaidIfPending(eq(10L), eq("PAY_RACE_2"), anyString(), any()))
                    .thenReturn(0); // 另一并发已抢先成功

            Order result = orderService.markPaid(10L, "PAY_RACE_2", "WECHAT");

            // 关键断言：并发失败方按幂等返回最终态，不抛错
            assertEquals(OrderStatus.PAID, result.getStatus());
            assertEquals("PAY_001", result.getPaymentNo());
        }

        @Test
        void differentPaymentNo_butOrderAlreadyPaid_isRejectedWithoutOverwrite() {
            // 第二个支付渠道（或回调重试）以不同 paymentNo 到达
            Order alreadyPaid = paidOrder("PAY_001");
            when(orderRepository.findById(10L)).thenReturn(Optional.of(alreadyPaid));

            Order result = orderService.markPaid(10L, "DIFFERENT_PAYNO", "ALIPAY");

            // 不抛错（避免给用户/回调方造成困惑），但坚决不覆盖原有 paymentNo
            assertEquals(OrderStatus.PAID, result.getStatus());
            assertEquals("PAY_001", result.getPaymentNo());
            verify(orderRepository, never())
                    .markPaidIfPending(anyLong(), anyString(), anyString(), any());
        }

        @Test
        void cancelledOrder_cannotBePaid() {
            Order cancelled = pendingOrder();
            cancelled.setStatus(OrderStatus.CANCELLED);
            when(orderRepository.findById(10L)).thenReturn(Optional.of(cancelled));

            assertThrows(IllegalArgumentException.class,
                    () -> orderService.markPaid(10L, "PAY_NEW", "WECHAT"));
            verify(orderRepository, never())
                    .markPaidIfPending(anyLong(), anyString(), anyString(), any());
        }
    }

    // -------------------- 取消订单 / 库存归还 --------------------

    @Nested
    class CancelStockRestore {

        @Test
        void cancelPending_restoresStockExactlyOnce() {
            Order pending = pendingOrder();
            Order cancelled = pendingOrder();
            cancelled.setStatus(OrderStatus.CANCELLED);

            when(orderRepository.findById(10L))
                    .thenReturn(Optional.of(pending))
                    .thenReturn(Optional.of(cancelled));
            when(orderRepository.cancelIfStatusIn(eq(10L), any(), any())).thenReturn(1);

            OrderItem item = new OrderItem();
            item.setBookId(1L);
            item.setQuantity(2);
            when(orderItemRepository.findByOrderId(10L)).thenReturn(List.of(item));

            Order result = orderService.cancelOrder(10L);

            assertEquals(OrderStatus.CANCELLED, result.getStatus());
            verify(bookRepository, times(1)).incrementStock(1L, 2);
        }

        @Test
        void cancelAlreadyCancelled_isIdempotent_noStockDoubleRestore() {
            Order cancelled = pendingOrder();
            cancelled.setStatus(OrderStatus.CANCELLED);
            when(orderRepository.findById(10L)).thenReturn(Optional.of(cancelled));

            Order result = orderService.cancelOrder(10L);

            assertEquals(OrderStatus.CANCELLED, result.getStatus());
            // 关键断言：再次取消不会再次归还库存
            verify(bookRepository, never()).incrementStock(anyLong(), anyInt());
            verify(orderRepository, never()).cancelIfStatusIn(anyLong(), any(), any());
        }

        @Test
        void cancelRace_cancelLosesToPay_doesNotRestoreStock() {
            // 用户点击取消的同一刻，支付已抢先成功；CAS 命中 0 行
            Order pending = pendingOrder();
            Order paid = paidOrder("PAY_001");
            when(orderRepository.findById(10L))
                    .thenReturn(Optional.of(pending))   // 第一次：仍是 PENDING
                    .thenReturn(Optional.of(paid));     // 第二次：CAS 失败后重新读取
            when(orderRepository.cancelIfStatusIn(eq(10L), any(), any())).thenReturn(0);

            assertThrows(IllegalArgumentException.class,
                    () -> orderService.cancelOrder(10L));
            // 关键断言：CAS 失败时绝不释放库存
            verify(bookRepository, never()).incrementStock(anyLong(), anyInt());
        }

        @Test
        void userCannotCancelPaidOrder() {
            Order paid = paidOrder("PAY_001");
            when(orderRepository.findById(10L))
                    .thenReturn(Optional.of(paid))
                    .thenReturn(Optional.of(paid));
            when(orderRepository.cancelIfStatusIn(eq(10L), any(), any())).thenReturn(0);

            assertThrows(IllegalArgumentException.class,
                    () -> orderService.cancelOrder(10L, false));
            verify(bookRepository, never()).incrementStock(anyLong(), anyInt());
        }
    }

    // -------------------- 状态机迁移 --------------------

    @Nested
    class TransitionStateMachine {

        @Test
        void invalidJump_PendingToShipped_isRejected() {
            Order pending = pendingOrder();
            when(orderRepository.findById(10L)).thenReturn(Optional.of(pending));

            assertThrows(IllegalArgumentException.class,
                    () -> orderService.transition(10L, OrderStatus.SHIPPED, "TRACK_001"));
            verify(orderRepository, never())
                    .transitionStatus(anyLong(), anyString(), anyString(), any(), any());
        }

        @Test
        void backwardTransition_PaidToPending_isRejected() {
            Order paid = paidOrder("PAY_001");
            when(orderRepository.findById(10L)).thenReturn(Optional.of(paid));

            assertThrows(IllegalArgumentException.class,
                    () -> orderService.transition(10L, OrderStatus.PENDING, null));
        }

        @Test
        void shippedToCompleted_onlyOnceEvenUnderRace() {
            Order shipped = pendingOrder();
            shipped.setStatus(OrderStatus.SHIPPED);
            Order completed = pendingOrder();
            completed.setStatus(OrderStatus.COMPLETED);

            when(orderRepository.findById(10L))
                    .thenReturn(Optional.of(shipped))
                    .thenReturn(Optional.of(completed));
            when(orderRepository.transitionStatus(
                    eq(10L), eq(OrderStatus.SHIPPED), eq(OrderStatus.COMPLETED), any(), any()))
                    .thenReturn(1);

            Order result = orderService.transition(10L, OrderStatus.COMPLETED, null);
            assertEquals(OrderStatus.COMPLETED, result.getStatus());
        }

        @Test
        void transitionToSameStatus_isIdempotent() {
            Order shipped = pendingOrder();
            shipped.setStatus(OrderStatus.SHIPPED);
            when(orderRepository.findById(10L)).thenReturn(Optional.of(shipped));

            Order result = orderService.transition(10L, OrderStatus.SHIPPED, null);
            assertEquals(OrderStatus.SHIPPED, result.getStatus());
            verify(orderRepository, never())
                    .transitionStatus(anyLong(), anyString(), anyString(), any(), any());
        }
    }

    // -------------------- 测试工具 --------------------

    private Order pendingOrder() {
        Order o = new Order();
        o.setId(10L);
        o.setOrderNo("ORD_T");
        o.setUserId(1L);
        o.setStatus(OrderStatus.PENDING);
        o.setTotalAmount(new BigDecimal("59.80"));
        o.setCreatedAt(LocalDateTime.now());
        o.setUpdatedAt(LocalDateTime.now());
        return o;
    }

    private Order paidOrder(String paymentNo) {
        Order o = pendingOrder();
        o.setStatus(OrderStatus.PAID);
        o.setPaymentNo(paymentNo);
        o.setPaidAt(LocalDateTime.now());
        return o;
    }
}
