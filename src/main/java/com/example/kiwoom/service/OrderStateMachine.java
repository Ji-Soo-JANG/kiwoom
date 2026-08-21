package com.example.kiwoom.service;

import com.example.kiwoom.dto.OrderStatus;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

public class OrderStateMachine {
    private static final Map<OrderStatus, EnumSet<OrderStatus>> TRANSITIONS = transitions();

    public void requireTransition(OrderStatus from, OrderStatus to) {
        if (!TRANSITIONS.getOrDefault(from, EnumSet.noneOf(OrderStatus.class)).contains(to)) {
            throw new IllegalStateException("허용되지 않은 주문 상태 전이입니다: " + from + " -> " + to);
        }
    }

    private static Map<OrderStatus, EnumSet<OrderStatus>> transitions() {
        Map<OrderStatus, EnumSet<OrderStatus>> values = new EnumMap<>(OrderStatus.class);
        values.put(OrderStatus.CREATED, EnumSet.of(OrderStatus.SUBMITTED, OrderStatus.REJECTED));
        values.put(
                OrderStatus.SUBMITTED,
                EnumSet.of(OrderStatus.ACKNOWLEDGED, OrderStatus.CANCELED, OrderStatus.REJECTED));
        values.put(
                OrderStatus.ACKNOWLEDGED,
                EnumSet.of(
                        OrderStatus.PARTIALLY_FILLED,
                        OrderStatus.FILLED,
                        OrderStatus.CANCELED,
                        OrderStatus.REJECTED));
        values.put(
                OrderStatus.PARTIALLY_FILLED,
                EnumSet.of(OrderStatus.PARTIALLY_FILLED, OrderStatus.FILLED, OrderStatus.CANCELED));
        return values;
    }
}
