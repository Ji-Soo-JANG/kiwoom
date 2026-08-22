package com.example.kiwoom.service;

import com.example.kiwoom.dto.StrategyCandidate;
import com.example.kiwoom.dto.StrategyScanResponse;
import com.example.kiwoom.repository.MarketDataRepository;
import com.example.kiwoom.repository.StrategySnapshotRepository;
import com.example.kiwoom.service.strategy.MultiPeriodRecoveryPullbackStrategy;
import com.example.kiwoom.service.strategy.StrategyRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class StrategyScanService {
    public static final String STRATEGY_VERSION = MultiPeriodRecoveryPullbackStrategy.VERSION_KEY;
    private static final String SCOPE = "로컬 DB에 저장된 현물 주식 일봉 대상";

    private final MarketDataRepository repository;
    private final StrategySnapshotRepository snapshots;
    private final LimitedTradingService limitedTrading;
    private final StrategyRegistry strategies;

    public StrategyScanService(
            MarketDataRepository repository,
            StrategySnapshotRepository snapshots,
            LimitedTradingService limitedTrading,
            StrategyRegistry strategies) {
        this.repository = repository;
        this.snapshots = snapshots;
        this.limitedTrading = limitedTrading;
        this.strategies = strategies;
    }

    public Mono<StrategyScanResponse> scan() {
        return scan(StrategyPatternDetector.DEFAULT_BASE_DAYS);
    }

    /**
     * @param boxRangeDays 박스권 횡보 기준 기간(거래일). 프론트엔드의 날짜 바에서 조절합니다.
     */
    public Mono<StrategyScanResponse> scan(int boxRangeDays) {
        return scan(boxRangeDays, null);
    }

    public Mono<StrategyScanResponse> scan(int boxRangeDays, LocalDate asOf) {
        return scan(STRATEGY_VERSION, boxRangeDays, asOf);
    }

    public Mono<StrategyScanResponse> scan(
            String strategyVersion, int boxRangeDays, LocalDate asOf) {
        var strategy = strategies.require(strategyVersion);
        var stocks =
                asOf == null
                        ? repository.findAnalyzableStocks()
                        : repository.findAnalyzableStocks(asOf);
        String scope = asOf == null ? SCOPE : SCOPE + " (시점 기준 " + asOf + ")";

        return stocks.flatMap(
                        stock ->
                                (asOf == null
                                                ? repository.findDailyPrices(
                                                        stock.code(),
                                                        strategy.requiredHistoryDays())
                                                : repository.findDailyPrices(
                                                        stock.code(),
                                                        strategy.requiredHistoryDays(),
                                                        asOf))
                                        .collectList()
                                        .map(
                                                prices ->
                                                        strategy.analyze(
                                                                stock, prices, boxRangeDays)),
                        8)
                .collectList()
                .flatMap(
                        candidates -> {
                            List<StrategyCandidate> sorted =
                                    candidates.stream()
                                            .sorted(
                                                    Comparator.comparingInt(
                                                                    StrategyCandidate::score)
                                                            .reversed()
                                                            .thenComparing(StrategyCandidate::code))
                                            .toList();
                            List<StrategyCandidate> displayed = sorted.stream().limit(30).toList();
                            Mono<LocalDate> latestTradeDate =
                                    asOf == null
                                            ? repository.findLatestTradeDate()
                                            : repository.findLatestTradeDate(asOf);
                            return latestTradeDate
                                    .map(Optional::of)
                                    .defaultIfEmpty(Optional.empty())
                                    .flatMap(
                                            dataAsOf ->
                                                    snapshots
                                                            .save(
                                                                    strategyVersion,
                                                                    boxRangeDays,
                                                                    sorted.size(),
                                                                    scope,
                                                                    dataAsOf.orElse(null),
                                                                    sorted,
                                                                    displayed)
                                                            .flatMap(
                                                                    response ->
                                                                            asOf == null
                                                                                    ? registerCandidates(
                                                                                            response)
                                                                                    : Mono.just(
                                                                                            response)));
                        });
    }

    Mono<StrategyScanResponse> registerCandidates(StrategyScanResponse response) {
        return reactor.core.publisher.Flux.fromIterable(response.candidates())
                .filter(StrategyCandidate::qualified)
                .filter(
                        candidate ->
                                candidate.currentPrice() > 0 && candidate.currentPrice() <= 100_000)
                .concatMap(
                        candidate ->
                                limitedTrading.create(
                                        new com.example.kiwoom.dto.TradeCandidateRequest(
                                                "scan-"
                                                        + response.scanId()
                                                        + '-'
                                                        + candidate.code(),
                                                candidate.code(),
                                                String.join(", ", candidate.matchedConditions()),
                                                BigDecimal.valueOf(candidate.currentPrice()),
                                                Math.max(1, 100_000 / candidate.currentPrice()),
                                                Instant.now().plus(1, ChronoUnit.DAYS))))
                .then(Mono.just(response));
    }

    public Mono<StrategyScanResponse> latestSnapshot() {
        return snapshots.findLatest();
    }
}
