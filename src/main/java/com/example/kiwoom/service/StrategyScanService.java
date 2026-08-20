package com.example.kiwoom.service;

import com.example.kiwoom.dto.StrategyCandidate;
import com.example.kiwoom.dto.StrategyScanResponse;
import com.example.kiwoom.repository.MarketDataRepository;
import java.time.Instant;
import java.util.Comparator;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class StrategyScanService {
    private final MarketDataRepository repository;
    private final StrategyPatternDetector detector = new StrategyPatternDetector();

    public StrategyScanService(MarketDataRepository repository) {
        this.repository = repository;
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
                .map(
                        candidates ->
                                new StrategyScanResponse(
                                        candidates.stream()
                                                .sorted(
                                                        Comparator.comparingInt(
                                                                        StrategyCandidate::score)
                                                                .reversed())
                                                .limit(30)
                                                .toList(),
                                        candidates.size(),
                                        "로컬 DB에 90개 이상 일봉이 저장된 전체 종목",
                                        Instant.now()));
    }
}
