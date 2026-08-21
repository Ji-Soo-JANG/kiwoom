package com.example.kiwoom.service;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.kiwoom.dto.OrderStatus;
import org.junit.jupiter.api.Test;

class OrderStateMachineTest {
    private final OrderStateMachine machine = new OrderStateMachine();

    @Test
    void acceptsNormalAndPartialFillLifecycle() {
        assertThatNoException()
                .isThrownBy(
                        () -> {
                            machine.requireTransition(OrderStatus.CREATED, OrderStatus.SUBMITTED);
                            machine.requireTransition(
                                    OrderStatus.SUBMITTED, OrderStatus.ACKNOWLEDGED);
                            machine.requireTransition(
                                    OrderStatus.ACKNOWLEDGED, OrderStatus.PARTIALLY_FILLED);
                            machine.requireTransition(
                                    OrderStatus.PARTIALLY_FILLED, OrderStatus.FILLED);
                        });
    }

    @Test
    void rejectsTransitionFromTerminalState() {
        assertThatThrownBy(
                        () -> machine.requireTransition(OrderStatus.FILLED, OrderStatus.SUBMITTED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("허용되지 않은");
    }
}
