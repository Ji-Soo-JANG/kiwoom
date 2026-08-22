package com.example.kiwoom.service;

import com.example.kiwoom.dto.DailyPriceResponse;
import com.example.kiwoom.dto.MarketDataSyncStatus;
import com.example.kiwoom.repository.MarketDataRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class MarketDataCollectionService {
    private static final Logger log = LoggerFactory.getLogger(MarketDataCollectionService.class);
    private static final int MAX_BATCH_SIZE = 500;
    private static final int DAILY_CANDLE_LIMIT = 1500;
    private final KiwoomApiService kiwoom;
    private final MarketDataRepository repository;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile RunSummary lastRun = new RunSummary(0, 0, 0);

    public MarketDataCollectionService(KiwoomApiService kiwoom, MarketDataRepository repository) {
        this.kiwoom = kiwoom;
        this.repository = repository;
    }

    public Mono<MarketDataSyncStatus> synchronize(int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, MAX_BATCH_SIZE));
        if (!running.compareAndSet(false, true)) return status();

        AtomicInteger processed = new AtomicInteger();
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        return kiwoom.getStockCatalog()
                .flatMap(catalog -> repository.saveStocks(Flux.fromIterable(catalog)))
                .thenMany(repository.findCodesToSync(limit))
                .concatMap(
                        code ->
                                synchronizeStock(code)
                                        .doOnNext(
                                                success -> {
                                                    processed.incrementAndGet();
                                                    if (success) succeeded.incrementAndGet();
                                                    else failed.incrementAndGet();
                                                }))
                .then(
                        Mono.defer(
                                () -> {
                                    lastRun =
                                            new RunSummary(
                                                    processed.get(), succeeded.get(), failed.get());
                                    return repository.status(
                                            processed.get(), succeeded.get(), failed.get(), false);
                                }))
                .doOnError(
                        error ->
                                log.warn(
                                        "market_data_sync_failed errorType={}",
                                        error.getClass().getSimpleName()))
                .doFinally(signal -> running.set(false));
    }

    private Mono<Boolean> synchronizeStock(String code) {
        return kiwoom.getDailyPrices(code, null, DAILY_CANDLE_LIMIT)
                .flatMap(prices -> savePrices(code, prices))
                .thenReturn(true)
                .onErrorResume(
                        error -> {
                            log.warn(
                                    "market_data_stock_sync_failed code={} errorType={}",
                                    code,
                                    error.getClass().getSimpleName());
                            return repository
                                    .markFailure(code, error.getMessage())
                                    .thenReturn(false);
                        });
    }

    private Mono<Void> savePrices(String code, List<DailyPriceResponse> prices) {
        LocalDate latest =
                prices.stream()
                        .map(DailyPriceResponse::getDate)
                        .map(value -> LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE))
                        .max(LocalDate::compareTo)
                        .orElse(null);
        if (latest == null) return repository.markFailure(code, "일봉 데이터 없음");
        return repository
                .saveCandles(code, Flux.fromIterable(prices))
                .then(repository.markSuccess(code, latest));
    }

    public Mono<MarketDataSyncStatus> status() {
        RunSummary summary = lastRun;
        return repository.status(
                summary.processed(), summary.succeeded(), summary.failed(), running.get());
    }

    private record RunSummary(int processed, int succeeded, int failed) {}
}
