package com.example.kiwoom.service;

import com.example.kiwoom.dto.OrderReconciliationReport;
import com.example.kiwoom.dto.OrderSide;
import com.example.kiwoom.dto.OrderStatus;
import com.example.kiwoom.dto.PaperAccountStatus;
import com.example.kiwoom.dto.PaperOrderRequest;
import com.example.kiwoom.dto.PaperPosition;
import com.example.kiwoom.dto.TradingMode;
import com.example.kiwoom.dto.TradingOrder;
import com.example.kiwoom.error.ResourceNotFoundException;
import com.example.kiwoom.error.TradingSafetyException;
import com.example.kiwoom.repository.PaperTradingRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class PaperOrderService implements com.example.kiwoom.service.broker.BrokerAdapter {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final TradingModeService modes;
    private final PaperTradingRepository repository;
    private final PaperRiskService risks;
    private final OrderStateMachine stateMachine = new OrderStateMachine();

    public PaperOrderService(
            TradingModeService modes, PaperTradingRepository repository, PaperRiskService risks) {
        this.modes = modes;
        this.repository = repository;
        this.risks = risks;
    }

    public Mono<TradingOrder> place(PaperOrderRequest request) {
        TradingMode mode = modes.requireOrderCreationMode();
        if (mode != TradingMode.PAPER) {
            return Mono.error(new TradingSafetyException("외부 실주문 전송은 아직 사용할 수 없습니다."));
        }
        return place(request, mode);
    }

    @Override
    public TradingMode mode() {
        return TradingMode.PAPER;
    }

    @Override
    public boolean externalSubmissionAvailable() {
        return false;
    }

    public Mono<TradingOrder> placeAutomatedPaper(PaperOrderRequest request) {
        return place(request, TradingMode.PAPER);
    }

    private Mono<TradingOrder> place(PaperOrderRequest request, TradingMode mode) {
        return account()
                .then(repository.createIdempotent(request, mode))
                .flatMap(order -> validateIdempotentRequest(order, request))
                .flatMap(
                        order ->
                                order.status() == OrderStatus.CREATED
                                        ? risks.validate(request)
                                                .then(submitAndFill(order))
                                                .onErrorResume(
                                                        TradingSafetyException.class,
                                                        error ->
                                                                repository.rejectCreated(
                                                                        order.id(),
                                                                        error.getMessage()))
                                        : Mono.just(order))
                .flatMap(
                        order ->
                                order.status() == OrderStatus.FILLED
                                        ? risks.status().thenReturn(order)
                                        : Mono.just(order));
    }

    public Flux<TradingOrder> findAll() {
        return repository.findAllOrders();
    }

    public Flux<PaperPosition> positions() {
        return repository.findPositions();
    }

    public Mono<PaperAccountStatus> account() {
        return repository.initializeAccount(
                modes.properties().paperInitialCash(), LocalDate.now(SEOUL));
    }

    public Mono<TradingOrder> cancel(long id) {
        return repository
                .findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("주문을 찾을 수 없습니다.")))
                .flatMap(
                        order -> {
                            if (order.status() == OrderStatus.FILLED
                                    || order.status() == OrderStatus.CANCELED
                                    || order.status() == OrderStatus.REJECTED) {
                                return Mono.error(
                                        new TradingSafetyException("완료된 주문은 취소할 수 없습니다."));
                            }
                            stateMachine.requireTransition(order.status(), OrderStatus.CANCELED);
                            return repository.transition(
                                    id, order.status(), OrderStatus.CANCELED, "사용자 취소");
                        });
    }

    public Mono<OrderReconciliationReport> reconcile() {
        return account()
                .flatMap(
                        account ->
                                Mono.zip(
                                                repository.findAllOrders().collectList(),
                                                repository.findAllFills().collectList(),
                                                repository.findPositions().collectList())
                                        .map(
                                                tuple ->
                                                        reconcile(
                                                                account,
                                                                tuple.getT1(),
                                                                tuple.getT2(),
                                                                tuple.getT3())));
    }

    private Mono<TradingOrder> submitAndFill(TradingOrder created) {
        stateMachine.requireTransition(OrderStatus.CREATED, OrderStatus.SUBMITTED);
        return repository
                .transition(created.id(), OrderStatus.CREATED, OrderStatus.SUBMITTED, "PAPER 주문 제출")
                .flatMap(
                        submitted -> {
                            stateMachine.requireTransition(
                                    OrderStatus.SUBMITTED, OrderStatus.ACKNOWLEDGED);
                            return repository.transition(
                                    submitted.id(),
                                    OrderStatus.SUBMITTED,
                                    OrderStatus.ACKNOWLEDGED,
                                    "PAPER 주문 접수");
                        })
                .flatMap(
                        acknowledged -> {
                            stateMachine.requireTransition(
                                    OrderStatus.ACKNOWLEDGED, OrderStatus.FILLED);
                            return repository.fillAcknowledged(acknowledged);
                        });
    }

    private Mono<TradingOrder> validateIdempotentRequest(
            TradingOrder order, PaperOrderRequest request) {
        boolean matches =
                order.code().equals(request.code())
                        && order.side() == request.side()
                        && order.requestedQuantity() == request.quantity()
                        && order.requestedPrice().compareTo(request.price()) == 0;
        return matches
                ? Mono.just(order)
                : Mono.error(new TradingSafetyException("같은 decisionId에 서로 다른 주문 요청을 사용할 수 없습니다."));
    }

    private OrderReconciliationReport reconcile(
            PaperAccountStatus account,
            List<TradingOrder> orders,
            List<PaperTradingRepository.StoredFill> fills,
            List<PaperPosition> positions) {
        List<String> mismatches = new ArrayList<>();
        Map<Long, Long> fillQuantityByOrder = new HashMap<>();
        Map<String, Long> expectedPositions = new HashMap<>();
        BigDecimal expectedCash = account.initialCash();
        for (var fill : fills) {
            fillQuantityByOrder.merge(fill.orderId(), fill.quantity(), Long::sum);
            long signedQuantity = fill.side() == OrderSide.BUY ? fill.quantity() : -fill.quantity();
            expectedPositions.merge(fill.code(), signedQuantity, Long::sum);
            BigDecimal amount = fill.price().multiply(BigDecimal.valueOf(fill.quantity()));
            expectedCash =
                    fill.side() == OrderSide.BUY
                            ? expectedCash.subtract(amount).subtract(fill.fee())
                            : expectedCash.add(amount).subtract(fill.fee()).subtract(fill.tax());
        }
        for (TradingOrder order : orders) {
            long fillQuantity = fillQuantityByOrder.getOrDefault(order.id(), 0L);
            if (fillQuantity != order.filledQuantity()) {
                mismatches.add(
                        "주문 "
                                + order.id()
                                + " 체결수량 불일치: 주문="
                                + order.filledQuantity()
                                + ", 체결="
                                + fillQuantity);
            }
        }
        Map<String, Long> storedPositions = new HashMap<>();
        positions.forEach(position -> storedPositions.put(position.code(), position.quantity()));
        expectedPositions.entrySet().stream()
                .filter(entry -> entry.getValue() != 0)
                .forEach(
                        entry -> {
                            long stored = storedPositions.getOrDefault(entry.getKey(), 0L);
                            if (stored != entry.getValue()) {
                                mismatches.add(
                                        "종목 "
                                                + entry.getKey()
                                                + " 잔고 불일치: 저장="
                                                + stored
                                                + ", 체결합="
                                                + entry.getValue());
                            }
                        });
        storedPositions.keySet().stream()
                .filter(code -> expectedPositions.getOrDefault(code, 0L) == 0)
                .forEach(code -> mismatches.add("체결 근거가 없는 저장 잔고: " + code));
        expectedCash = expectedCash.setScale(4, RoundingMode.HALF_UP);
        if (account.cash().compareTo(expectedCash) != 0) {
            mismatches.add("현금 불일치: 저장=" + account.cash() + ", 체결합=" + expectedCash);
        }
        return new OrderReconciliationReport(
                "PAPER_LOCAL",
                orders.size(),
                fills.size(),
                account.cash(),
                expectedCash,
                List.copyOf(mismatches),
                mismatches.isEmpty(),
                Instant.now());
    }
}
