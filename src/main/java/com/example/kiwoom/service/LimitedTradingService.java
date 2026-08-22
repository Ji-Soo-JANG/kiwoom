package com.example.kiwoom.service;

import com.example.kiwoom.dto.LimitedTradeCandidate;
import com.example.kiwoom.dto.OrderSide;
import com.example.kiwoom.dto.PaperOrderRequest;
import com.example.kiwoom.dto.PerformanceSampleRequest;
import com.example.kiwoom.dto.TradeCandidateRequest;
import com.example.kiwoom.dto.TradingPerformanceStatus;
import com.example.kiwoom.error.ResourceNotFoundException;
import com.example.kiwoom.error.TradingSafetyException;
import com.example.kiwoom.repository.LimitedTradingRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class LimitedTradingService {
    public static final String APPROVAL_CONFIRMATION = "APPROVE_LIMITED_TRADE";
    private static final BigDecimal MAX_ORDER_AMOUNT = new BigDecimal("100000");
    private static final int MAX_DAILY_ORDERS = 2;
    private static final int MAX_POSITIONS = 1;
    private static final int MIN_MONITOR_SAMPLES = 5;
    private static final BigDecimal MAX_AVERAGE_SLIPPAGE = new BigDecimal("0.01");
    private static final BigDecimal MIN_AVERAGE_RETURN = BigDecimal.ZERO;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final LimitedTradingRepository repository;
    private final PaperOrderService orders;
    private final PaperRiskService risks;
    private final PaperTradeCycleService tradeCycles;
    private final AutoTradingControlService autoTrading;

    public LimitedTradingService(
            LimitedTradingRepository repository,
            PaperOrderService orders,
            PaperRiskService risks,
            PaperTradeCycleService tradeCycles,
            AutoTradingControlService autoTrading) {
        this.repository = repository;
        this.orders = orders;
        this.risks = risks;
        this.tradeCycles = tradeCycles;
        this.autoTrading = autoTrading;
    }

    public Mono<LimitedTradeCandidate> create(TradeCandidateRequest request) {
        return repository
                .create(request)
                .flatMap(
                        candidate ->
                                candidate.code().equals(request.code())
                                                && candidate
                                                                .referencePrice()
                                                                .compareTo(request.referencePrice())
                                                        == 0
                                                && candidate.suggestedQuantity()
                                                        == request.suggestedQuantity()
                                                && candidate.reason().equals(request.reason())
                                        ? Mono.just(candidate)
                                        : Mono.error(
                                                new TradingSafetyException(
                                                        "같은 signalId에 서로 다른 후보를 저장할 수 없습니다.")))
                .flatMap(
                        candidate ->
                                autoTrading
                                        .paperEnabledFor(StrategyScanService.STRATEGY_VERSION)
                                        .flatMap(
                                                enabled ->
                                                        enabled
                                                                ? execute(candidate, "paper-auto")
                                                                        .onErrorReturn(candidate)
                                                                : Mono.just(candidate)));
    }

    public Flux<LimitedTradeCandidate> findAll() {
        return repository.findAll();
    }

    public Mono<LimitedTradeCandidate> approve(long id, String confirmation, String approvedBy) {
        if (!APPROVAL_CONFIRMATION.equals(confirmation)) {
            return Mono.error(new TradingSafetyException("제한 주문 승인 확인 문구가 일치하지 않습니다."));
        }
        return repository
                .findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("매매 후보를 찾을 수 없습니다.")))
                .flatMap(candidate -> execute(candidate, approvedBy));
    }

    private Mono<LimitedTradeCandidate> execute(LimitedTradeCandidate pending, String approvedBy) {
        return validateCandidate(pending)
                .then(repository.approve(pending.id(), approvedBy))
                .switchIfEmpty(Mono.error(new TradingSafetyException("만료되었거나 이미 처리된 후보입니다.")))
                .flatMap(
                        candidate ->
                                orders.placeAutomatedPaper(
                                                new PaperOrderRequest(
                                                        "approved-signal-" + candidate.signalId(),
                                                        candidate.code(),
                                                        OrderSide.BUY,
                                                        candidate.suggestedQuantity(),
                                                        candidate.referencePrice()))
                                        .flatMap(
                                                order ->
                                                        recordEntry(candidate, order)
                                                                .then(
                                                                        tradeCycles.open(
                                                                                candidate.id(),
                                                                                order))
                                                                .then(
                                                                        repository.linkOrder(
                                                                                candidate.id(),
                                                                                order.id())))
                                        .onErrorResume(
                                                error ->
                                                        repository
                                                                .resetPending(candidate.id())
                                                                .then(Mono.error(error))));
    }

    private Mono<Void> recordEntry(
            LimitedTradeCandidate candidate, com.example.kiwoom.dto.TradingOrder order) {
        if (order.averageFillPrice() == null) return Mono.empty();
        BigDecimal slippage =
                order.averageFillPrice()
                        .subtract(candidate.referencePrice())
                        .divide(candidate.referencePrice(), 8, RoundingMode.HALF_UP);
        return repository.addEntryExecution(candidate, order, slippage);
    }

    public Mono<LimitedTradeCandidate> reject(long id) {
        return repository
                .reject(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("매매 후보를 찾을 수 없습니다.")));
    }

    public Mono<TradingPerformanceStatus> recordPerformance(PerformanceSampleRequest request) {
        BigDecimal slippage =
                request.actualPrice()
                        .subtract(request.expectedPrice())
                        .divide(request.expectedPrice(), 8, RoundingMode.HALF_UP);
        return repository
                .addPerformance(request, slippage)
                .then(repository.performance())
                .flatMap(this::haltIfDegraded);
    }

    public Mono<TradingPerformanceStatus> performance() {
        return repository.performance().flatMap(this::withCurrentHaltStatus);
    }

    private Mono<Void> validateCandidate(LimitedTradeCandidate candidate) {
        BigDecimal amount =
                candidate
                        .referencePrice()
                        .multiply(BigDecimal.valueOf(candidate.suggestedQuantity()));
        if (amount.compareTo(MAX_ORDER_AMOUNT) > 0) {
            return Mono.error(new TradingSafetyException("제한 주문 금액 100,000원을 초과합니다."));
        }
        LocalDate today = LocalDate.now(SEOUL);
        return Mono.zip(
                        orders.positions().count(),
                        orders.findAll()
                                .filter(
                                        order ->
                                                LocalDate.ofInstant(order.createdAt(), SEOUL)
                                                        .equals(today))
                                .count(),
                        risks.status())
                .flatMap(
                        state -> {
                            if (state.getT3().killSwitchActive()) {
                                return Mono.error(new TradingSafetyException("킬 스위치가 활성화되어 있습니다."));
                            }
                            if (state.getT1() >= MAX_POSITIONS) {
                                return Mono.error(
                                        new TradingSafetyException("제한 매매는 한 종목만 보유할 수 있습니다."));
                            }
                            if (state.getT2() >= MAX_DAILY_ORDERS) {
                                return Mono.error(
                                        new TradingSafetyException("제한 매매 일일 주문 횟수를 초과했습니다."));
                            }
                            return Mono.empty();
                        });
    }

    private Mono<TradingPerformanceStatus> haltIfDegraded(TradingPerformanceStatus status) {
        String reason = degradationReason(status);
        return reason == null
                ? withCurrentHaltStatus(status)
                : risks.activateAutomatically("PERFORMANCE_DEGRADATION: " + reason)
                        .map(
                                risk ->
                                        new TradingPerformanceStatus(
                                                status.sampleCount(),
                                                status.averageSlippageRate(),
                                                status.averageNetReturnRate(),
                                                status.maximumSlippageRate(),
                                                true,
                                                risk.killSwitchReason(),
                                                status.evaluatedAt()));
    }

    private Mono<TradingPerformanceStatus> withCurrentHaltStatus(TradingPerformanceStatus status) {
        return risks.status()
                .map(
                        risk ->
                                new TradingPerformanceStatus(
                                        status.sampleCount(),
                                        status.averageSlippageRate(),
                                        status.averageNetReturnRate(),
                                        status.maximumSlippageRate(),
                                        risk.killSwitchActive(),
                                        risk.killSwitchReason(),
                                        status.evaluatedAt()));
    }

    private String degradationReason(TradingPerformanceStatus status) {
        if (status.sampleCount() < MIN_MONITOR_SAMPLES) return null;
        if (status.averageSlippageRate().abs().compareTo(MAX_AVERAGE_SLIPPAGE) > 0) {
            return "평균 슬리피지 1% 초과";
        }
        if (status.averageNetReturnRate().compareTo(MIN_AVERAGE_RETURN) < 0) {
            return "비용 후 평균 수익률 음수";
        }
        return null;
    }
}
