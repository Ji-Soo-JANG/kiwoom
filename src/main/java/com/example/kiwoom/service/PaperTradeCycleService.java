package com.example.kiwoom.service;

import com.example.kiwoom.dto.OrderSide;
import com.example.kiwoom.dto.PaperOrderRequest;
import com.example.kiwoom.dto.PaperTradeCycle;
import com.example.kiwoom.dto.PaperTradeResult;
import com.example.kiwoom.dto.TradePerformanceSummary;
import com.example.kiwoom.dto.TradingOrder;
import com.example.kiwoom.error.ResourceNotFoundException;
import com.example.kiwoom.error.TradingSafetyException;
import com.example.kiwoom.repository.PaperTradeCycleRepository;
import com.example.kiwoom.repository.PaperTradingRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class PaperTradeCycleService {
    public static final String EXIT_CONFIRMATION = "APPROVE_PAPER_EXIT";
    private final PaperTradeCycleRepository cycles;
    private final PaperTradingRepository trading;
    private final PaperOrderService orders;
    private final AutoTradingControlService autoTrading;

    public PaperTradeCycleService(
            PaperTradeCycleRepository cycles,
            PaperTradingRepository trading,
            PaperOrderService orders,
            AutoTradingControlService autoTrading) {
        this.cycles = cycles;
        this.trading = trading;
        this.orders = orders;
        this.autoTrading = autoTrading;
    }

    public Mono<PaperTradeCycle> open(long candidateId, TradingOrder order) {
        return cycles.open(
                candidateId,
                order.code(),
                order.filledQuantity(),
                order.id(),
                order.averageFillPrice());
    }

    public Flux<PaperTradeCycle> findAll() {
        return cycles.findAll();
    }

    public Flux<PaperTradeResult> results() {
        return cycles.results();
    }

    public Mono<Void> evaluate(String code, BigDecimal price, Instant observedAt) {
        return cycles.findHoldingByCode(code)
                .concatMap(
                        cycle ->
                                exitReason(cycle, price, observedAt)
                                        .map(
                                                reason ->
                                                        cycles.requestExit(
                                                                        cycle.id(),
                                                                        reason,
                                                                        price,
                                                                        observedAt)
                                                                .flatMap(
                                                                        pending ->
                                                                                autoTrading
                                                                                        .paperEnabledFor(
                                                                                                AutoTradingControlService
                                                                                                        .DEFAULT_STRATEGY)
                                                                                        .flatMap(
                                                                                                enabled ->
                                                                                                        enabled
                                                                                                                ? approveExit(
                                                                                                                        pending
                                                                                                                                .id(),
                                                                                                                        EXIT_CONFIRMATION)
                                                                                                                : Mono
                                                                                                                        .just(
                                                                                                                                pending))))
                                        .orElseGet(Mono::empty))
                .then();
    }

    public Mono<PaperTradeCycle> approveExit(long id, String confirmation) {
        if (!EXIT_CONFIRMATION.equals(confirmation))
            return Mono.error(new TradingSafetyException("PAPER 청산 승인 확인 문구가 일치하지 않습니다."));
        return cycles.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("PAPER 매매 주기를 찾을 수 없습니다.")))
                .flatMap(
                        cycle -> {
                            if (!"EXIT_PENDING".equals(cycle.status()))
                                return Mono.error(
                                        new TradingSafetyException("청산 대기 상태에서만 승인할 수 있습니다."));
                            return trading.findPosition(cycle.code())
                                    .switchIfEmpty(
                                            Mono.error(
                                                    new TradingSafetyException(
                                                            "매도할 PAPER 포지션이 없습니다.")))
                                    .flatMap(
                                            position ->
                                                    position.quantity() < cycle.quantity()
                                                            ? Mono.error(
                                                                    new TradingSafetyException(
                                                                            "PAPER 보유 수량을 초과한 청산입니다."))
                                                            : orders.placeAutomatedPaper(
                                                                    new PaperOrderRequest(
                                                                            "exit-cycle-"
                                                                                    + cycle.id(),
                                                                            cycle.code(),
                                                                            OrderSide.SELL,
                                                                            cycle.quantity(),
                                                                            cycle
                                                                                    .exitTriggerPrice())))
                                    .flatMap(order -> cycles.close(cycle.id(), order.id()))
                                    .flatMap(closed -> createResult(closed).thenReturn(closed));
                        });
    }

    public Mono<TradePerformanceSummary> summary() {
        return cycles.results().collectList().map(this::summarize);
    }

    private java.util.Optional<String> exitReason(PaperTradeCycle c, BigDecimal price, Instant at) {
        if (price.compareTo(c.stopLossPrice()) <= 0) return java.util.Optional.of("STOP_LOSS");
        if (price.compareTo(c.takeProfitPrice()) >= 0) return java.util.Optional.of("TAKE_PROFIT");
        if (!at.isBefore(c.openedAt().plus(Duration.ofDays(c.maxHoldingDays()))))
            return java.util.Optional.of("MAX_HOLDING");
        return java.util.Optional.empty();
    }

    private Mono<Void> createResult(PaperTradeCycle c) {
        return trading.findAllFills()
                .filter(f -> f.orderId() == c.entryOrderId() || f.orderId() == c.exitOrderId())
                .collectList()
                .flatMap(
                        fills -> {
                            BigDecimal buy = amount(fills, OrderSide.BUY),
                                    sell = amount(fills, OrderSide.SELL);
                            BigDecimal costs =
                                    fills.stream()
                                            .map(f -> f.fee().add(f.tax()))
                                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                            BigDecimal net = sell.subtract(buy).subtract(costs);
                            BigDecimal rate =
                                    buy.signum() == 0
                                            ? BigDecimal.ZERO
                                            : net.divide(buy, 8, RoundingMode.HALF_UP);
                            int days =
                                    (int)
                                            Math.max(
                                                    0,
                                                    Duration.between(c.openedAt(), c.closedAt())
                                                            .toDays());
                            return cycles.saveResult(
                                    new PaperTradeResult(
                                            c.id(),
                                            sell.subtract(buy),
                                            costs,
                                            net,
                                            rate,
                                            days,
                                            c.exitReason(),
                                            c.closedAt()));
                        });
    }

    private BigDecimal amount(List<PaperTradingRepository.StoredFill> fills, OrderSide side) {
        return fills.stream()
                .filter(f -> f.side() == side)
                .map(f -> f.price().multiply(BigDecimal.valueOf(f.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private TradePerformanceSummary summarize(List<PaperTradeResult> values) {
        List<PaperTradeResult> sorted =
                values.stream().sorted(Comparator.comparing(PaperTradeResult::closedAt)).toList();
        long wins = sorted.stream().filter(r -> r.netPnl().signum() > 0).count();
        List<BigDecimal> gains =
                sorted.stream()
                        .map(PaperTradeResult::netReturnRate)
                        .filter(v -> v.signum() > 0)
                        .toList();
        List<BigDecimal> losses =
                sorted.stream()
                        .map(PaperTradeResult::netReturnRate)
                        .filter(v -> v.signum() < 0)
                        .toList();
        BigDecimal grossGain = gains.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal grossLoss =
                losses.stream().map(BigDecimal::abs).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal equity = BigDecimal.ZERO, peak = BigDecimal.ZERO, maxDd = BigDecimal.ZERO;
        int consecutive = 0;
        for (PaperTradeResult r : sorted) {
            equity = equity.add(r.netPnl());
            peak = peak.max(equity);
            if (peak.signum() > 0)
                maxDd = maxDd.max(peak.subtract(equity).divide(peak, 8, RoundingMode.HALF_UP));
            consecutive = r.netPnl().signum() < 0 ? consecutive + 1 : 0;
        }
        int recentStart = Math.max(0, sorted.size() - 10);
        BigDecimal recent =
                average(
                        sorted.subList(recentStart, sorted.size()).stream()
                                .map(PaperTradeResult::netReturnRate)
                                .toList());
        return new TradePerformanceSummary(
                sorted.size(),
                wins,
                ratio(wins, sorted.size()),
                average(gains),
                average(losses),
                average(losses).signum() == 0
                        ? BigDecimal.ZERO
                        : average(gains).divide(average(losses).abs(), 8, RoundingMode.HALF_UP),
                grossLoss.signum() == 0
                        ? BigDecimal.ZERO
                        : grossGain.divide(grossLoss, 8, RoundingMode.HALF_UP),
                consecutive,
                recent,
                maxDd,
                sorted.stream()
                        .map(PaperTradeResult::netPnl)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private BigDecimal average(List<BigDecimal> v) {
        return v.isEmpty()
                ? BigDecimal.ZERO
                : v.stream()
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(v.size()), 8, RoundingMode.HALF_UP);
    }

    private BigDecimal ratio(long n, long d) {
        return d == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(n).divide(BigDecimal.valueOf(d), 8, RoundingMode.HALF_UP);
    }
}
