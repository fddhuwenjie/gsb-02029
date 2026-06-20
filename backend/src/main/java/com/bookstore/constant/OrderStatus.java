package com.bookstore.constant;

import java.util.Map;
import java.util.Set;

/**
 * 订单状态机：
 *
 *   PENDING ──pay──▶ PAID ──ship──▶ SHIPPED ──complete──▶ COMPLETED
 *      │
 *      ├──cancel(用户/超时)──▶ CANCELLED  (释放库存)
 *      │
 *      └──cancel(管理员)──▶ CANCELLED   (释放库存，仅 PENDING/PAID 允许)
 *
 * 已 SHIPPED / COMPLETED / CANCELLED 的订单视为终态，禁止任何回退或重复扭转。
 */
public final class OrderStatus {
    public static final String PENDING = "PENDING";
    public static final String PAID = "PAID";
    public static final String SHIPPED = "SHIPPED";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";

    /**
     * 终态集合：进入这些状态后不允许再被修改。
     */
    public static final Set<String> TERMINAL = Set.of(COMPLETED, CANCELLED);

    /**
     * 合法的状态转移图，key 为当前状态，value 为允许迁移到的下一组状态。
     */
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            PENDING, Set.of(PAID, CANCELLED),
            PAID, Set.of(SHIPPED, CANCELLED),
            SHIPPED, Set.of(COMPLETED),
            COMPLETED, Set.of(),
            CANCELLED, Set.of()
    );

    public static boolean canTransition(String from, String to) {
        if (from == null || to == null) {
            return false;
        }
        Set<String> allowed = TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    public static boolean isTerminal(String status) {
        return TERMINAL.contains(status);
    }

    private OrderStatus() {}
}
