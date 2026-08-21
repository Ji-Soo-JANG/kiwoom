package com.example.kiwoom.service;

import com.example.kiwoom.dto.OrderStatus;
import com.example.kiwoom.dto.PaperBrokerVerificationReport;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Deterministic local-only lifecycle check. This service never sends an external order. */
@Service
public class PaperBrokerVerificationService {
    private final OrderStateMachine stateMachine = new OrderStateMachine();

    public PaperBrokerVerificationReport verify() {
        List<String> trace = new ArrayList<>();
        Scenario order = new Scenario(10, 50_000);
        order.move(OrderStatus.SUBMITTED, stateMachine, trace);
        order.move(OrderStatus.ACKNOWLEDGED, stateMachine, trace);
        boolean unfilled = order.filled == 0 && order.status == OrderStatus.ACKNOWLEDGED;
        order.amend(8, 49_500, trace);
        boolean amendment = order.quantity == 8 && order.price == 49_500;
        order.fill("execution-1", 3, stateMachine, trace);
        boolean partialFill = order.status == OrderStatus.PARTIALLY_FILLED && order.filled == 3;
        order.fill("execution-1", 3, stateMachine, trace);
        boolean duplicateIgnored = order.filled == 3;
        Scenario recovered = order.recover(trace);
        boolean recovery = recovered.status == order.status && recovered.filled == order.filled;
        recovered.move(OrderStatus.CANCELED, stateMachine, trace);
        return new PaperBrokerVerificationReport(
                Instant.now(),
                partialFill,
                unfilled,
                amendment,
                recovered.status == OrderStatus.CANCELED,
                recovery,
                duplicateIgnored,
                List.copyOf(trace));
    }

    private static final class Scenario {
        private int quantity;
        private long price;
        private int filled;
        private OrderStatus status = OrderStatus.CREATED;
        private final Set<String> executions = new HashSet<>();

        private Scenario(int quantity, long price) {
            this.quantity = quantity;
            this.price = price;
        }

        private void move(OrderStatus next, OrderStateMachine machine, List<String> trace) {
            machine.requireTransition(status, next);
            trace.add(status + " -> " + next);
            status = next;
        }

        private void amend(int quantity, long price, List<String> trace) {
            if (status != OrderStatus.ACKNOWLEDGED && status != OrderStatus.PARTIALLY_FILLED)
                throw new IllegalStateException("접수 또는 부분 체결 주문만 정정할 수 있습니다.");
            if (quantity < filled) throw new IllegalArgumentException("정정 수량은 체결 수량보다 작을 수 없습니다.");
            this.quantity = quantity;
            this.price = price;
            trace.add("AMENDED quantity=" + quantity + ", price=" + price);
        }

        private void fill(String id, int quantity, OrderStateMachine machine, List<String> trace) {
            if (!executions.add(id)) {
                trace.add("DUPLICATE_EXECUTION_IGNORED " + id);
                return;
            }
            filled += quantity;
            move(
                    filled < this.quantity ? OrderStatus.PARTIALLY_FILLED : OrderStatus.FILLED,
                    machine,
                    trace);
        }

        private Scenario recover(List<String> trace) {
            Scenario copy = new Scenario(quantity, price);
            copy.filled = filled;
            copy.status = status;
            copy.executions.addAll(executions);
            trace.add("RECOVERED status=" + status + ", filled=" + filled);
            return copy;
        }
    }
}
