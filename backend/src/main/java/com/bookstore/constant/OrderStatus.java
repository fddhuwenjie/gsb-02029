package com.bookstore.constant;

import java.util.Set;

public final class OrderStatus {
    public static final String PENDING = "PENDING";
    public static final String PAYING = "PAYING";
    public static final String PAID = "PAID";
    public static final String SHIPPED = "SHIPPED";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";
    public static final String CLOSED = "CLOSED";

    public static final Set<String> PAID_STATUSES = Set.of(PAID, SHIPPED, COMPLETED);
    public static final Set<String> FINISHED_STATUSES = Set.of(COMPLETED, CANCELLED, CLOSED);
    public static final Set<String> CAN_CANCEL_STATUSES = Set.of(PENDING, PAYING);
    public static final Set<String> CAN_SHIP_STATUSES = Set.of(PAID);
    public static final Set<String> CAN_COMPLETE_STATUSES = Set.of(SHIPPED);

    private OrderStatus() {}

    public static boolean canTransit(String from, String to) {
        if (from == null || to == null) return false;
        if (from.equals(to)) return true;
        return switch (from) {
            case PENDING -> Set.of(PAYING, CANCELLED, CLOSED, PAID).contains(to);
            case PAYING -> Set.of(PAID, CANCELLED, CLOSED, PENDING).contains(to);
            case PAID -> Set.of(SHIPPED, CANCELLED).contains(to);
            case SHIPPED -> Set.of(COMPLETED).contains(to);
            case COMPLETED, CANCELLED, CLOSED -> false;
            default -> false;
        };
    }
}
