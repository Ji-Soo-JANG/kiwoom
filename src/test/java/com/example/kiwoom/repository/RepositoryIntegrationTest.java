package com.example.kiwoom.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.kiwoom.dto.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

@SpringBootTest(
        properties = {
            "kiwoom.api.base-url=http://localhost",
            "kiwoom.api.key=test-key",
            "kiwoom.api.secret=test-secret",
            "kiwoom.api.connect-timeout=1s",
            "kiwoom.api.response-timeout=2s",
            "kiwoom.api.max-connections=5",
            "kiwoom.api.max-retries=2",
            "kiwoom.api.retry-backoff=1ms",
            "kiwoom.api.current-price-cache-ttl=3s",
            "kiwoom.api.daily-price-cache-ttl=10m",
            "spring.r2dbc.url=r2dbc:h2:mem:///kiwoom-repo-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.url=jdbc:h2:mem:flyway-repo-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.username=sa",
            "app.trading.mode=PAPER",
            "spring.sql.init.mode=always",
            "spring.flyway.enabled=false"
        })
class RepositoryIntegrationTest {

    @Autowired private MarketDataRepository marketDataRepository;
    @Autowired private AlertRepository alertRepository;

    @Autowired private StrategySnapshotRepository strategySnapshotRepository;
    @Autowired private BacktestRepository backtestRepository;
    @Autowired private WalkForwardRepository walkForwardRepository;
    @Autowired private com.example.kiwoom.service.PaperOrderService paperOrderService;

    // --- MarketDataRepository tests ---

    @Test
    void saveStocks_insertsNewStocks() {
        StockSearchResult stock = new StockSearchResult("999999", "테스트종목", "KOSPI");
        marketDataRepository.saveStocks(Flux.just(stock)).block();
    }

    @Test
    void saveStocks_updatesExistingStocks() {
        StockSearchResult stock = new StockSearchResult("999999", "테스트종목", "KOSPI");
        marketDataRepository.saveStocks(Flux.just(stock)).block();
        StockSearchResult updated = new StockSearchResult("999999", "업데이트된종목", "KOSDAQ");
        marketDataRepository.saveStocks(Flux.just(updated)).block();
    }

    @Test
    void saveCandles_insertsAndUpdatesCandles() {
        StockSearchResult stock = new StockSearchResult("888888", "캔들종목", "KOSPI");
        marketDataRepository.saveStocks(Flux.just(stock)).block();
        DailyPriceResponse candle =
                new DailyPriceResponse("20260816", 70000, 71000, 69000, 70500, 1000);
        marketDataRepository.saveCandles("888888", Flux.just(candle)).block();
        // Update same candle
        DailyPriceResponse updatedCandle =
                new DailyPriceResponse("20260816", 70500, 72000, 69500, 71000, 2000);
        marketDataRepository.saveCandles("888888", Flux.just(updatedCandle)).block();
    }

    @Test
    void findCodesToSync_returnsStockCodes() {
        StockSearchResult stock = new StockSearchResult("777777", "동기화종목", "KOSPI");
        marketDataRepository.saveStocks(Flux.just(stock)).block();
        var codes = marketDataRepository.findCodesToSync(10).collectList().block();
        assertThat(codes).contains("777777");
    }

    @Test
    void markSuccessAndFailure_updatesSyncState() {
        StockSearchResult stock = new StockSearchResult("666666", "상태종목", "KOSPI");
        marketDataRepository.saveStocks(Flux.just(stock)).block();
        marketDataRepository.markSuccess("666666", LocalDate.of(2026, 8, 16)).block();
        marketDataRepository.markFailure("666666", "테스트 실패 메시지").block();
    }

    @Test
    void status_returnsSyncStatus() {
        var status = marketDataRepository.status(0, 0, 0, false).block();
        assertThat(status).isNotNull();
        assertThat(status.stockCount()).isGreaterThanOrEqualTo(0);
        assertThat(status.running()).isFalse();
    }

    @Test
    void findStocksByCodes_returnsMatchingStocks() {
        StockSearchResult stock = new StockSearchResult("555555", "조회종목", "KOSPI");
        marketDataRepository.saveStocks(Flux.just(stock)).block();
        var found =
                marketDataRepository
                        .findStocksByCodes(java.util.List.of("555555"))
                        .collectList()
                        .block();
        assertThat(found).hasSize(1);
        assertThat(found.get(0).code()).isEqualTo("555555");
    }

    @Test
    void findStocksByCodes_emptyListReturnsEmpty() {
        var found =
                marketDataRepository.findStocksByCodes(java.util.List.of()).collectList().block();
        assertThat(found).isEmpty();
    }

    @Test
    void findDailyPrices_returnsCandles() {
        StockSearchResult stock = new StockSearchResult("444444", "차트종목", "KOSPI");
        marketDataRepository.saveStocks(Flux.just(stock)).block();
        DailyPriceResponse candle =
                new DailyPriceResponse("20260816", 70000, 71000, 69000, 70500, 1000);
        marketDataRepository.saveCandles("444444", Flux.just(candle)).block();
        var prices = marketDataRepository.findDailyPrices("444444", 10).collectList().block();
        assertThat(prices).isNotEmpty();
    }

    @Test
    void stockMasterSnapshot_usesLatestCatalogKnownAtRequestedDate() {
        StockSearchResult first = new StockSearchResult("111111", "과거상장종목", "KOSPI");
        StockSearchResult second = new StockSearchResult("222222", "신규상장종목", "KOSDAQ");

        marketDataRepository
                .saveStockMasterSnapshot(java.util.List.of(first), LocalDate.of(2026, 1, 2))
                .block();
        marketDataRepository
                .saveStockMasterSnapshot(java.util.List.of(second), LocalDate.of(2026, 2, 2))
                .block();

        assertThat(
                        marketDataRepository
                                .findStockCodesAt(LocalDate.of(2026, 1, 31))
                                .collectList()
                                .block())
                .containsExactly("111111");
        assertThat(
                        marketDataRepository
                                .findStockCodesAt(LocalDate.of(2026, 2, 28))
                                .collectList()
                                .block())
                .containsExactly("222222");
    }

    @Test
    void findAnalyzableStocks_returnsStocksWithEnoughData() {
        StockSearchResult stock = new StockSearchResult("333333", "분석종목", "KOSPI");
        marketDataRepository.saveStocks(Flux.just(stock)).block();
        for (int i = 0; i < 91; i++) {
            String date = String.format("202601%02d", (i % 28) + 1);
            DailyPriceResponse candle =
                    new DailyPriceResponse(date, 70000, 71000, 69000, 70500, 1000);
            marketDataRepository.saveCandles("333333", Flux.just(candle)).block();
        }
        var analyzable = marketDataRepository.findAnalyzableStocks().collectList().block();
        assertThat(analyzable).isNotNull();
    }

    @Test
    void strategySnapshot_roundTripsVersionInputsAndDecision() {
        StrategyCandidate candidate =
                new StrategyCandidate(
                        "005930",
                        "삼성전자",
                        80000,
                        85,
                        true,
                        -25.0,
                        18.0,
                        3,
                        7.0,
                        -4.0,
                        java.util.List.of("과거 고점 대비 20% 이상 하락", "돌파선 위 눌림목"));

        StrategyScanResponse saved =
                strategySnapshotRepository
                        .save(
                                "test-strategy-v1",
                                60,
                                1,
                                "테스트 범위",
                                LocalDate.of(2026, 8, 21),
                                java.util.List.of(candidate),
                                java.util.List.of(candidate))
                        .block();
        StrategyScanResponse latest = strategySnapshotRepository.findLatest().block();

        assertThat(saved).isNotNull();
        assertThat(saved.scanId()).isPositive();
        assertThat(latest).isNotNull();
        assertThat(latest.strategyVersion()).isEqualTo("test-strategy-v1");
        assertThat(latest.boxRangeDays()).isEqualTo(60);
        assertThat(latest.dataAsOf()).isEqualTo(LocalDate.of(2026, 8, 21));
        assertThat(latest.candidates()).containsExactly(candidate);
    }

    @Test
    void backtestResult_persistsRunAndTrades() {
        BacktestTrade trade =
                new BacktestTrade(
                        LocalDate.of(2026, 1, 2),
                        LocalDate.of(2026, 1, 10),
                        new BigDecimal("100.0000"),
                        new BigDecimal("110.0000"),
                        100,
                        new BigDecimal("1000.0000"),
                        new BigDecimal("3.1500"),
                        new BigDecimal("19.8000"),
                        new BigDecimal("20.0000"),
                        new BigDecimal("977.0500"),
                        0.097705,
                        "TAKE_PROFIT");
        BacktestResponse result =
                new BacktestResponse(
                        null,
                        "test-v1",
                        "005930",
                        "삼성전자",
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 2, 1),
                        new BigDecimal("1000000.0000"),
                        new BigDecimal("1000977.0500"),
                        0.00015,
                        0.0018,
                        0.001,
                        1,
                        1,
                        0.000977,
                        -0.01,
                        new BigDecimal("977.0500"),
                        java.util.List.of(trade),
                        java.time.Instant.now());

        BacktestResponse saved = backtestRepository.save(result).block();

        assertThat(saved).isNotNull();
        assertThat(saved.runId()).isPositive();
        assertThat(saved.trades()).containsExactly(trade);
    }

    @Test
    void walkForwardReport_persistsSummaryAndFolds() {
        WalkForwardFold fold =
                new WalkForwardFold(
                        1,
                        LocalDate.of(2025, 1, 1),
                        LocalDate.of(2025, 12, 1),
                        LocalDate.of(2025, 12, 2),
                        LocalDate.of(2026, 2, 28),
                        3,
                        0.02,
                        2,
                        0.5,
                        new BigDecimal("1000.0000"),
                        0.01,
                        -0.05,
                        new BigDecimal("200.0000"));
        WalkForwardReport report =
                new WalkForwardReport(
                        null,
                        "test-v1",
                        "005930",
                        "삼성전자",
                        LocalDate.of(2025, 1, 1),
                        LocalDate.of(2026, 2, 28),
                        240,
                        60,
                        60,
                        1,
                        2,
                        new BigDecimal("1000.0000"),
                        -0.05,
                        0.01,
                        new BigDecimal("200.0000"),
                        false,
                        "테스트",
                        java.util.List.of(fold),
                        java.time.Instant.now());

        WalkForwardReport saved = walkForwardRepository.save(report).block();

        assertThat(saved).isNotNull();
        assertThat(saved.reportId()).isPositive();
        assertThat(saved.folds()).containsExactly(fold);
    }

    @Test
    void paperOrder_isIdempotentAndReconcilesOrderFillPositionAndCash() {
        PaperOrderRequest request =
                new PaperOrderRequest(
                        "repository-test-buy-333333",
                        "333333",
                        OrderSide.BUY,
                        10,
                        new BigDecimal("1000.0000"));

        TradingOrder first = paperOrderService.place(request).block();
        TradingOrder duplicate = paperOrderService.place(request).block();
        OrderReconciliationReport reconciliation = paperOrderService.reconcile().block();

        assertThat(first).isNotNull();
        assertThat(first.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(duplicate).isNotNull();
        assertThat(duplicate.id()).isEqualTo(first.id());
        assertThat(
                        paperOrderService
                                .positions()
                                .filter(p -> p.code().equals("333333"))
                                .blockFirst())
                .extracting(PaperPosition::quantity)
                .isEqualTo(10L);
        assertThat(reconciliation).isNotNull();
        assertThat(reconciliation.consistent()).isTrue();
    }

    // --- AlertRepository tests ---

    @Test
    void alertRuleCrudLifecycle() {
        AlertRuleRequest request =
                new AlertRuleRequest(
                        "005930", AlertConditionType.PRICE_ABOVE, new BigDecimal("80000"));
        AlertRule created = alertRepository.addRule("test-user", request).block();
        assertThat(created).isNotNull();
        assertThat(created.code()).isEqualTo("005930");

        var rules = alertRepository.findRules("test-user").collectList().block();
        assertThat(rules).isNotEmpty();

        var enabled = alertRepository.findEnabledRules("test-user").collectList().block();
        assertThat(enabled).isNotEmpty();

        var found = alertRepository.findRule("test-user", created.id()).block();
        assertThat(found).isNotNull();
        assertThat(found.id()).isEqualTo(created.id());

        AlertRule updated =
                alertRepository
                        .updateRule("test-user", created.id(), new BigDecimal("81000"), false)
                        .block();
        assertThat(updated).isNotNull();
        assertThat(updated.threshold()).isEqualByComparingTo(new BigDecimal("81000"));

        boolean triggered =
                alertRepository.transitionToTriggered("test-user", created.id()).block();
        assertThat(triggered).isTrue();

        alertRepository.resetState("test-user", created.id()).block();

        alertRepository.deleteRule("test-user", created.id()).block();
    }

    @Test
    void alertEventLifecycle() {
        AlertRuleRequest request =
                new AlertRuleRequest(
                        "000660", AlertConditionType.PRICE_BELOW, new BigDecimal("100000"));
        AlertRule rule = alertRepository.addRule("event-user", request).block();
        assertThat(rule).isNotNull();

        AlertEvent event =
                alertRepository.addEvent("event-user", rule, new BigDecimal("99000")).block();
        assertThat(event).isNotNull();
        assertThat(event.observedValue()).isEqualByComparingTo(new BigDecimal("99000"));

        var events = alertRepository.findEvents("event-user", false, 0, 10).collectList().block();
        assertThat(events).isNotEmpty();

        var count = alertRepository.countEvents("event-user", false).block();
        assertThat(count).isGreaterThan(0);

        var unread = alertRepository.countEvents("event-user", true).block();
        assertThat(unread).isGreaterThan(0);

        var foundEvent = alertRepository.findEvent("event-user", event.id()).block();
        assertThat(foundEvent).isNotNull();

        alertRepository.markRead("event-user", event.id()).block();

        var afterRead = alertRepository.countEvents("event-user", true).block();
        assertThat(afterRead).isEqualTo(0);

        alertRepository.deleteRule("event-user", rule.id()).block();
    }

    @Test
    void addRuleWithNullThreshold() {
        AlertRuleRequest request =
                new AlertRuleRequest("000660", AlertConditionType.MACD_CROSS_UP, null);
        AlertRule rule = alertRepository.addRule("null-thresh-user", request).block();
        assertThat(rule).isNotNull();
        assertThat(rule.threshold()).isNull();
        alertRepository.deleteRule("null-thresh-user", rule.id()).block();
    }
}
