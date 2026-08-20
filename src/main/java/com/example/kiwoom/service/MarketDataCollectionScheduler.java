package com.example.kiwoom.service;

import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.market-data.scheduler.enabled", havingValue = "true")
public class MarketDataCollectionScheduler {
    private static final Logger log = LoggerFactory.getLogger(MarketDataCollectionScheduler.class);
    private final MarketDataCollectionService collection;
    private final int batchSize;
    private final AtomicBoolean running = new AtomicBoolean();

    public MarketDataCollectionScheduler(
            MarketDataCollectionService collection,
            @Value("${app.market-data.scheduler.batch-size:100}") int batchSize) {
        this.collection = collection;
        this.batchSize = batchSize;
    }

    @Scheduled(cron = "${app.market-data.scheduler.cron:0 10 16 * * MON-FRI}", zone = "Asia/Seoul")
    public void synchronize() {
        if (!running.compareAndSet(false, true)) return;
        collection
                .synchronize(batchSize)
                .doOnNext(
                        status ->
                                log.info(
                                        "scheduled_market_data_sync_completed processed={} succeeded={} failed={}",
                                        status.processedInLastRun(),
                                        status.succeededInLastRun(),
                                        status.failedInLastRun()))
                .doOnError(
                        error ->
                                log.warn(
                                        "scheduled_market_data_sync_failed type={}",
                                        error.getClass().getSimpleName()))
                .doFinally(signal -> running.set(false))
                .subscribe();
    }
}
