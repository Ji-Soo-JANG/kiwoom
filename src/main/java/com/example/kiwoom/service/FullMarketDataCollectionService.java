package com.example.kiwoom.service;

import com.example.kiwoom.dto.DailyPriceResponse;
import com.example.kiwoom.dto.MarketDataSyncStatus;
import com.example.kiwoom.dto.StockSearchResult;
import com.example.kiwoom.repository.MarketDataRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 전체 종목의 일봉 데이터를 키움 REST API로 조회해 PostgreSQL(Docker DB)에 저장하는 일괄 수집기입니다.
 *
 * <p>기존 {@link MarketDataCollectionService}가 요청 단위로 소량(기본 20건)을 증분 수집하는 것과 달리 이 클래스는 종목 마스터 전체를
 * 저장하고 모든 종목의 최근 일봉을 수집합니다. 저장 테이블은 기존 V8 마이그레이션의 stock_master, daily_candle,
 * market_data_sync_state를 공유합니다.
 *
 * <p>트리거는 세 가지이며 스케줄·기동 시 실행은 기본 비활성화되어 있습니다. application.properties를 고치지 않고 환경변수로만 조절합니다.
 *
 * <ul>
 *   <li>프로그램에서 {@link #synchronizeAll()} 직접 호출
 *   <li>서버 기동 시 실행: {@code APP_MARKET_DATA_FULL_SYNC_RUN_ON_STARTUP=true}
 *   <li>장 마감 후 스케줄 실행: {@code APP_MARKET_DATA_FULL_SYNC_SCHEDULER_ENABLED=true}, {@code
 *       APP_MARKET_DATA_FULL_SYNC_CRON}(기본 "0 30 16 * * MON-FRI", Asia/Seoul), {@code
 *       APP_MARKET_DATA_FULL_SYNC_CONCURRENCY}(기본 1, 키움 호출 한도 대비 낮게 유지)
 * </ul>
 */
@Component
public class FullMarketDataCollectionService implements ApplicationRunner {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Logger log =
            LoggerFactory.getLogger(FullMarketDataCollectionService.class);
    private static final int DAILY_CANDLE_LIMIT = 500;
    private static final int PROGRESS_LOG_INTERVAL = 100;

    private final KiwoomApiService kiwoom;
    private final MarketDataRepository repository;
    private final int concurrency;
    private final boolean runOnStartup;
    private final boolean scheduledEnabled;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile RunSummary lastRun = new RunSummary(0, 0, 0);
    private volatile List<StockSearchResult> lastCatalog = List.of();

    public FullMarketDataCollectionService(
            KiwoomApiService kiwoom,
            MarketDataRepository repository,
            @Value("${app.market-data.full-sync.concurrency:1}") int concurrency,
            @Value("${app.market-data.full-sync.run-on-startup:false}") boolean runOnStartup,
            @Value("${app.market-data.full-sync.scheduler-enabled:false}")
                    boolean scheduledEnabled) {
        this.kiwoom = kiwoom;
        this.repository = repository;
        this.concurrency = Math.max(1, concurrency);
        this.runOnStartup = runOnStartup;
        this.scheduledEnabled = scheduledEnabled;
    }

    /** 키움 종목 마스터 전체를 저장한 뒤 모든 종목의 최근 일봉을 수집해 DB에 반영합니다. 실행 중이면 새 수집을 시작하지 않고 현재 상태를 반환합니다. */
    public Mono<MarketDataSyncStatus> synchronizeAll() {
        if (!running.compareAndSet(false, true)) {
            log.info("full_market_data_sync_already_running");
            return status();
        }

        AtomicInteger processed = new AtomicInteger();
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        return loadCatalog()
                .flatMap(this::saveCatalog)
                .flatMapMany(load -> Flux.fromIterable(load.stocks()))
                .flatMap(
                        stock ->
                                synchronizeStock(stock.code())
                                        .doOnNext(
                                                success ->
                                                        onStockFinished(
                                                                processed, succeeded, failed,
                                                                success)),
                        concurrency)
                .then(Mono.defer(() -> finishRun(processed, succeeded, failed)))
                .doOnError(
                        error ->
                                log.warn(
                                        "full_market_data_sync_failed errorType={}",
                                        error.getClass().getSimpleName()))
                .doFinally(signal -> running.set(false));
    }

    /** 최신 종목 목록을 키움에서 새로 받고, 실패하면 마지막으로 성공했던 목록으로 대체합니다. */
    private Mono<CatalogLoad> loadCatalog() {
        return kiwoom.refreshStockCatalog()
                .flatMap(status -> kiwoom.getStockCatalog())
                .doOnNext(items -> lastCatalog = List.copyOf(items))
                .map(items -> new CatalogLoad(items, true))
                .onErrorResume(
                        error -> {
                            log.warn(
                                    "full_market_data_catalog_refresh_failed fallbackToCached={} errorType={}",
                                    !lastCatalog.isEmpty(),
                                    error.getClass().getSimpleName());
                            return Mono.just(new CatalogLoad(lastCatalog, false));
                        });
    }

    private Mono<CatalogLoad> saveCatalog(CatalogLoad load) {
        if (load.stocks().isEmpty()) {
            log.warn("full_market_data_sync_catalog_empty");
            return Mono.empty();
        }
        Mono<Void> saveCurrent = repository.saveStocks(Flux.fromIterable(load.stocks()));
        Mono<Void> saveSnapshot =
                load.fresh()
                        ? repository.saveStockMasterSnapshot(load.stocks(), LocalDate.now(SEOUL))
                        : Mono.empty();
        return saveCurrent.then(saveSnapshot).thenReturn(load);
    }

    private Mono<Boolean> synchronizeStock(String code) {
        return kiwoom.getDailyPrices(code, null, DAILY_CANDLE_LIMIT)
                .flatMap(prices -> savePrices(code, prices))
                .thenReturn(true)
                .onErrorResume(
                        error -> {
                            log.warn(
                                    "full_market_data_stock_failed code={} errorType={}",
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
                        .map(date -> LocalDate.parse(date, DateTimeFormatter.BASIC_ISO_DATE))
                        .max(LocalDate::compareTo)
                        .orElse(null);
        if (latest == null) {
            return repository.markFailure(code, "일봉 데이터 없음");
        }
        return repository
                .saveCandles(code, Flux.fromIterable(prices))
                .then(repository.markSuccess(code, latest));
    }

    private void onStockFinished(
            AtomicInteger processed,
            AtomicInteger succeeded,
            AtomicInteger failed,
            boolean success) {
        processed.incrementAndGet();
        if (success) succeeded.incrementAndGet();
        else failed.incrementAndGet();
        if (processed.get() % PROGRESS_LOG_INTERVAL == 0) {
            log.info(
                    "full_market_data_sync_progress processed={} succeeded={} failed={}",
                    processed.get(),
                    succeeded.get(),
                    failed.get());
        }
    }

    private Mono<MarketDataSyncStatus> finishRun(
            AtomicInteger processed, AtomicInteger succeeded, AtomicInteger failed) {
        lastRun = new RunSummary(processed.get(), succeeded.get(), failed.get());
        log.info(
                "full_market_data_sync_completed processed={} succeeded={} failed={}",
                processed.get(),
                succeeded.get(),
                failed.get());
        return repository.status(processed.get(), succeeded.get(), failed.get(), false);
    }

    public Mono<MarketDataSyncStatus> status() {
        RunSummary summary = lastRun;
        return repository.status(
                summary.processed(), summary.succeeded(), summary.failed(), running.get());
    }

    @Scheduled(cron = "${app.market-data.full-sync.cron:0 30 16 * * MON-FRI}", zone = "Asia/Seoul")
    public void scheduledSynchronizeAll() {
        if (!scheduledEnabled) return;
        log.info("scheduled_full_market_data_sync_started");
        synchronizeAll()
                .doOnNext(
                        status ->
                                log.info(
                                        "scheduled_full_market_data_sync_completed processed={} succeeded={} failed={}",
                                        status.processedInLastRun(),
                                        status.succeededInLastRun(),
                                        status.failedInLastRun()))
                .doOnError(
                        error ->
                                log.warn(
                                        "scheduled_full_market_data_sync_failed errorType={}",
                                        error.getClass().getSimpleName()))
                .subscribe();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!runOnStartup) return;
        log.info("startup_full_market_data_sync_started");
        synchronizeAll()
                .doOnNext(
                        status ->
                                log.info(
                                        "startup_full_market_data_sync_completed processed={} succeeded={} failed={}",
                                        status.processedInLastRun(),
                                        status.succeededInLastRun(),
                                        status.failedInLastRun()))
                .doOnError(
                        error ->
                                log.warn(
                                        "startup_full_market_data_sync_failed errorType={}",
                                        error.getClass().getSimpleName()))
                .subscribe();
    }

    private record RunSummary(int processed, int succeeded, int failed) {}

    private record CatalogLoad(List<StockSearchResult> stocks, boolean fresh) {}
}
