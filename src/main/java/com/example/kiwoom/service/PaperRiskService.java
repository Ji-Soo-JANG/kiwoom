package com.example.kiwoom.service;

import com.example.kiwoom.config.TradingProperties;
import com.example.kiwoom.dto.KillSwitchResumeRequest;
import com.example.kiwoom.dto.OrderSide;
import com.example.kiwoom.dto.OrderStatus;
import com.example.kiwoom.dto.PaperAccountStatus;
import com.example.kiwoom.dto.PaperOrderRequest;
import com.example.kiwoom.dto.PaperPosition;
import com.example.kiwoom.dto.PaperRiskStatus;
import com.example.kiwoom.error.TradingSafetyException;
import com.example.kiwoom.repository.PaperTradingRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class PaperRiskService {
    public static final String RESUME_CONFIRMATION = "RESUME_PAPER_TRADING";
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final PaperTradingRepository repository;
    private final TradingProperties limits;
    private final OrderStateMachine stateMachine = new OrderStateMachine();

    public PaperRiskService(PaperTradingRepository repository, TradingProperties limits) {
        this.repository = repository;
        this.limits = limits;
    }

    public Mono<PaperRiskStatus> status() {
        return repository
                .initializeAccount(limits.paperInitialCash(), LocalDate.now(SEOUL))
                .then(Mono.zip(repository.findAccount(), repository.findPositions().collectList()))
                .flatMap(
                        tuple -> {
                            BigDecimal equity = equity(tuple.getT1(), tuple.getT2());
                            return repository
                                    .updateRiskSnapshot(equity, LocalDate.now(SEOUL))
                                    .then(repository.findAccount())
                                    .flatMap(
                                            account ->
                                                    activateWhenBreached(account, equity)
                                                            .then(repository.findAccount())
                                                            .map(
                                                                    refreshed ->
                                                                            toStatus(
                                                                                    refreshed,
                                                                                    tuple.getT2(),
                                                                                    equity)));
                        });
    }

    public Mono<Void> validate(PaperOrderRequest request) {
        return status().flatMap(
                        risk -> {
                            if (request.side() == OrderSide.SELL) return Mono.empty();
                            if (risk.killSwitchActive()) {
                                return Mono.error(
                                        new TradingSafetyException(
                                                "킬 스위치가 활성화되어 신규 매수가 차단되었습니다: "
                                                        + risk.killSwitchReason()));
                            }
                            BigDecimal amount =
                                    request.price()
                                            .multiply(BigDecimal.valueOf(request.quantity()));
                            if (amount.compareTo(risk.cash()) > 0) {
                                return Mono.error(
                                        new TradingSafetyException("PAPER 주문 가능 현금이 부족합니다."));
                            }
                            return repository
                                    .findPosition(request.code())
                                    .map(position -> positionValue(position).add(amount))
                                    .defaultIfEmpty(amount)
                                    .flatMap(
                                            nextPositionValue -> {
                                                BigDecimal positionRate =
                                                        ratio(nextPositionValue, risk.equity());
                                                if (positionRate.compareTo(limits.maxPositionRate())
                                                        > 0) {
                                                    return Mono.error(
                                                            new TradingSafetyException(
                                                                    "종목당 최대 비중을 초과합니다."));
                                                }
                                                BigDecimal grossRate =
                                                        ratio(
                                                                risk.grossExposure().add(amount),
                                                                risk.equity());
                                                if (grossRate.compareTo(
                                                                limits.maxGrossExposureRate())
                                                        > 0) {
                                                    return Mono.error(
                                                            new TradingSafetyException(
                                                                    "총 투자 비중 한도를 초과합니다."));
                                                }
                                                return repository
                                                        .findPosition(request.code())
                                                        .hasElement()
                                                        .flatMap(
                                                                alreadyHeld ->
                                                                        !alreadyHeld
                                                                                        && risk
                                                                                                        .openPositionCount()
                                                                                                >= limits
                                                                                                        .maxOpenPositions()
                                                                                ? Mono.error(
                                                                                        new TradingSafetyException(
                                                                                                "최대 동시 보유 종목 수를 초과합니다."))
                                                                                : Mono.empty());
                                            });
                        });
    }

    public Mono<PaperRiskStatus> activate(String reason) {
        return repository
                .activateKillSwitch("MANUAL: " + reason)
                .then(cancelOpenOrders())
                .then(status());
    }

    public Mono<PaperRiskStatus> resume(KillSwitchResumeRequest request) {
        if (!RESUME_CONFIRMATION.equals(request.confirmation())) {
            return Mono.error(new TradingSafetyException("킬 스위치 수동 재개 확인 문구가 일치하지 않습니다."));
        }
        return Mono.zip(repository.findAccount(), repository.findPositions().collectList())
                .flatMap(
                        tuple -> {
                            BigDecimal equity = equity(tuple.getT1(), tuple.getT2());
                            return repository
                                    .resumeKillSwitch(equity, LocalDate.now(SEOUL))
                                    .then(status());
                        });
    }

    private Mono<Void> activateWhenBreached(PaperAccountStatus account, BigDecimal equity) {
        if (account.killSwitchActive()) return Mono.empty();
        double dailyReturn = ratio(equity, account.dayStartEquity()).doubleValue() - 1;
        double drawdown = ratio(equity, account.peakEquity()).doubleValue() - 1;
        String reason = null;
        if (dailyReturn <= limits.maxDailyLossRate().negate().doubleValue()) {
            reason = "일일 손실 한도 초과: " + percent(dailyReturn);
        } else if (drawdown <= limits.maxDrawdownRate().negate().doubleValue()) {
            reason = "누적 낙폭 한도 초과: " + percent(drawdown);
        }
        return reason == null
                ? Mono.empty()
                : repository.activateKillSwitch(reason).then(cancelOpenOrders());
    }

    private Mono<Void> cancelOpenOrders() {
        return repository
                .findAllOrders()
                .filter(
                        order ->
                                order.status() == OrderStatus.CREATED
                                        || order.status() == OrderStatus.SUBMITTED
                                        || order.status() == OrderStatus.ACKNOWLEDGED
                                        || order.status() == OrderStatus.PARTIALLY_FILLED)
                .concatMap(
                        order -> {
                            stateMachine.requireTransition(order.status(), OrderStatus.CANCELED);
                            return repository.transition(
                                    order.id(), order.status(), OrderStatus.CANCELED, "킬 스위치 활성화");
                        })
                .then();
    }

    private PaperRiskStatus toStatus(
            PaperAccountStatus account, List<PaperPosition> positions, BigDecimal equity) {
        BigDecimal gross =
                positions.stream()
                        .map(this::positionValue)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PaperRiskStatus(
                equity,
                account.cash(),
                gross,
                positions.size(),
                round(ratio(equity, account.dayStartEquity()).doubleValue() - 1),
                round(ratio(equity, account.peakEquity()).doubleValue() - 1),
                limits.maxPositionRate(),
                limits.maxGrossExposureRate(),
                limits.maxDailyLossRate(),
                limits.maxDrawdownRate(),
                limits.maxOpenPositions(),
                account.killSwitchActive(),
                account.killSwitchReason(),
                Instant.now());
    }

    private BigDecimal equity(PaperAccountStatus account, List<PaperPosition> positions) {
        return account.cash()
                .add(
                        positions.stream()
                                .map(this::positionValue)
                                .reduce(BigDecimal.ZERO, BigDecimal::add))
                .setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal positionValue(PaperPosition position) {
        return position.averagePrice().multiply(BigDecimal.valueOf(position.quantity()));
    }

    private BigDecimal ratio(BigDecimal value, BigDecimal reference) {
        return reference.signum() == 0
                ? BigDecimal.ZERO
                : value.divide(reference, 10, RoundingMode.HALF_UP);
    }

    private double round(double value) {
        return Math.round(value * 1_000_000.0) / 1_000_000.0;
    }

    private String percent(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f%%", value * 100);
    }
}
