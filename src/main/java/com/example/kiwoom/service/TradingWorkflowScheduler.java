package com.example.kiwoom.service;

import com.example.kiwoom.dto.ObservationSampleRequest;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
@ConditionalOnProperty(name = "app.trading.scheduler.enabled", havingValue = "true")
public class TradingWorkflowScheduler {
    private static final Logger log = LoggerFactory.getLogger(TradingWorkflowScheduler.class);
    private final MarketDataCollectionService marketData;
    private final StrategyScanService scans;
    private final ObservationService observations;
    private final MarketCalendarService calendar;
    private final int batchSize;
    private final AtomicBoolean running = new AtomicBoolean();

    public TradingWorkflowScheduler(
            MarketDataCollectionService marketData,
            StrategyScanService scans,
            ObservationService observations,
            MarketCalendarService calendar,
            @Value("${app.trading.scheduler.batch-size:100}") int batchSize) {
        this.marketData = marketData;
        this.scans = scans;
        this.observations = observations;
        this.calendar = calendar;
        this.batchSize = batchSize;
    }

    @Scheduled(cron = "${app.trading.scheduler.cron:0 40 15 * * MON-FRI}", zone = "Asia/Seoul")
    public void run() {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
        if (!calendar.isTradingDay(today) || !running.compareAndSet(false, true)) return;
        marketData
                .synchronize(batchSize)
                .then(scans.scan())
                .flatMap(
                        response ->
                                observations
                                        .latestOrCreate(response.strategyVersion())
                                        .flatMap(
                                                report ->
                                                        Flux.fromIterable(response.candidates())
                                                                .concatMap(
                                                                        candidate ->
                                                                                observations
                                                                                        .addSample(
                                                                                                report
                                                                                                        .id(),
                                                                                                new ObservationSampleRequest(
                                                                                                        today,
                                                                                                        candidate
                                                                                                                .code(),
                                                                                                        candidate
                                                                                                                .qualified(),
                                                                                                        candidate
                                                                                                                .qualified(),
                                                                                                        candidate
                                                                                                                .currentPrice(),
                                                                                                        candidate
                                                                                                                .currentPrice())))
                                                                .then(
                                                                        observations.report(
                                                                                report.id()))))
                .doOnNext(
                        report ->
                                log.info(
                                        "scheduled_trading_workflow_completed observedDays={} samples={}",
                                        report.observedTradingDays(),
                                        report.sampleCount()))
                .doOnError(
                        error ->
                                log.warn(
                                        "scheduled_trading_workflow_failed type={}",
                                        error.getClass().getSimpleName()))
                .doFinally(signal -> running.set(false))
                .subscribe();
    }
}
