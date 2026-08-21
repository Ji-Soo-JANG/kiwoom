package com.example.kiwoom.service;

import com.example.kiwoom.dto.StrategyCandidate;
import com.example.kiwoom.dto.StrategyScanResponse;
import com.example.kiwoom.repository.MarketDataRepository;
import com.example.kiwoom.repository.StrategySnapshotRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class StrategyScanService {
    public static final String STRATEGY_VERSION = "drop-base-breakout-pullback-v1";
    private static final String SCOPE = "로컬 DB에 90개 이상 일봉이 저장된 전체 종목";

    private final MarketDataRepository repository;
    private final StrategySnapshotRepository snapshots;
    private final StrategyPatternDetector detector = new StrategyPatternDetector();

    public StrategyScanService(
            MarketDataRepository repository, StrategySnapshotRepository snapshots) {
        this.repository = repository;
        this.snapshots = snapshots;
    }

    public Mono<StrategyScanResponse> scan() {
        return scan(StrategyPatternDetector.DEFAULT_BASE_DAYS);
    }

    /**
     * @param boxRangeDays 박스권 횡보 기준 기간(거래일). 프론트엔드의 날짜 바에서 조절합니다.
     */
    public Mono<StrategyScanResponse> scan(int boxRangeDays) {
        return repository
                .findAnalyzableStocks()
                .flatMap(
                        stock ->
                                repository
                                        .findDailyPrices(stock.code(), 250)
                                        .collectList()
                                        .map(
                                                prices ->
                                                        detector.analyze(
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
                            return repository
                                    .findLatestTradeDate()
                                    .map(Optional::of)
                                    .defaultIfEmpty(Optional.empty())
                                    .flatMap(
                                            dataAsOf ->
                                                    snapshots.save(
                                                            STRATEGY_VERSION,
                                                            boxRangeDays,
                                                            sorted.size(),
                                                            SCOPE,
                                                            dataAsOf.orElse(null),
                                                            sorted,
                                                            displayed));
                        });
    }

    public Mono<StrategyScanResponse> latestSnapshot() {
        return snapshots.findLatest();
    }
}
