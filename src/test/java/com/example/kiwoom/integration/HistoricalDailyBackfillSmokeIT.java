package com.example.kiwoom.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.kiwoom.broker.kiwoom.client.ContinuationToken;
import com.example.kiwoom.broker.kiwoom.client.DailyChartPage;
import com.example.kiwoom.service.HistoricalBackfillState;
import com.example.kiwoom.service.HistoricalDailyBackfillService;
import com.example.kiwoom.service.KiwoomApiService;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;

/**
 * Explicit, one-stock authenticated smoke test for TASK-003 Gate 1.
 *
 * <p>It is deliberately disabled unless {@code KIWOOM_HISTORICAL_SMOKE=true} is supplied by the
 * operator. It never prints credentials, OAuth tokens, or continuation-token values.
 */
@SpringBootTest(
        properties = {
            "app.alert.scheduler.enabled=false",
            "app.market-data.scheduler.enabled=false",
            "app.trading.scheduler.enabled=false",
            "app.trading.swing-monitor.enabled=false"
        })
class HistoricalDailyBackfillSmokeIT {
    private static final LocalDate TARGET = LocalDate.of(2015, 1, 1);
    private static final DateTimeFormatter BASIC = DateTimeFormatter.BASIC_ISO_DATE;

    @Autowired private KiwoomApiService api;
    @Autowired private HistoricalDailyBackfillService backfill;
    @Autowired private DatabaseClient database;

    @Test
    void runsOneAuthenticatedHistoricalBackfillAndRecordsSanitizedEvidence() {
        Assumptions.assumeTrue(
                "true".equalsIgnoreCase(System.getenv("KIWOOM_HISTORICAL_SMOKE")),
                "set KIWOOM_HISTORICAL_SMOKE=true to opt in");
        String code = requiredStockCode();

        CandleSummary before = candleSummary(code);
        BackfillSummary stateBefore = backfillSummary(code);
        LocalDate baseDate =
                before.minDate() == null
                        ? LocalDate.now(ZoneId.of("Asia/Seoul"))
                        : before.minDate().minusDays(1);

        DailyChartPage first =
                api.getDailyChartPage(code, baseDate, null).block(Duration.ofSeconds(30));
        assertNotNull(first, "first ka10081 page is required");
        assertFalse(first.candles().isEmpty(), "first ka10081 page must contain candles");
        assertTrue(
                first.candles().stream()
                        .allMatch(
                                candle ->
                                        !LocalDate.parse(candle.getDate(), BASIC)
                                                .isAfter(baseDate)),
                "first page must not contain a date after base_dt");

        boolean continuationObserved = first.continuationAvailable();
        boolean movedOlder = false;
        if (continuationObserved) {
            ContinuationToken token = first.continuationToken();
            assertNotNull(token, "cont-yn=Y must include a next-key");
            assertTrue(token.present(), "cont-yn=Y must include a non-blank next-key");
            DailyChartPage second =
                    api.getDailyChartPage(code, baseDate, token).block(Duration.ofSeconds(30));
            assertNotNull(second, "continuation request must return a page");
            assertFalse(second.candles().isEmpty(), "continuation request must return candles");
            LocalDate firstOldest = oldest(first);
            LocalDate secondOldest = oldest(second);
            movedOlder = secondOldest.isBefore(firstOldest);
            assertTrue(movedOlder, "continuation must advance toward older candles");
        }

        HistoricalBackfillState result =
                backfill.backfill(code, TARGET).block(Duration.ofMinutes(5));
        assertNotNull(result, "backfill must return persisted historical state");
        CandleSummary after = candleSummary(code);
        BackfillSummary stateAfter = backfillSummary(code);
        assertEqualsOrGreater(
                after.count(), before.count(), "backfill must preserve existing candles");
        assertTrue(after.duplicates() == 0, "daily_candle must have no duplicate natural keys");
        assertTrue(after.invalidOhlc() == 0, "daily_candle must have valid OHLC values");

        System.out.printf(
                "GATE1_SMOKE before=%s after=%s stateBefore=%s stateAfter=%s baseDate=%s "
                        + "firstPageRows=%d continuationObserved=%s movedOlder=%s resultStatus=%s "
                        + "resultPages=%d resultCandles=%d%n",
                before,
                after,
                stateBefore,
                stateAfter,
                baseDate,
                first.candles().size(),
                continuationObserved,
                movedOlder,
                result.status(),
                result.pageCount(),
                result.candleCount());
    }

    private String requiredStockCode() {
        String code = System.getenv("KIWOOM_LIVE_STOCK_CODE");
        assertNotNull(code, "KIWOOM_LIVE_STOCK_CODE is required");
        assertTrue(code.matches("\\d{6}"), "KIWOOM_LIVE_STOCK_CODE must be six digits");
        return code;
    }

    private LocalDate oldest(DailyChartPage page) {
        return page.candles().stream()
                .map(candle -> LocalDate.parse(candle.getDate(), BASIC))
                .min(LocalDate::compareTo)
                .orElseThrow();
    }

    private CandleSummary candleSummary(String code) {
        return database.sql(
                        """
                        SELECT MIN(trade_date) AS min_date, MAX(trade_date) AS max_date, COUNT(*) AS row_count,
                               COUNT(*) - COUNT(DISTINCT trade_date) AS duplicates,
                               COUNT(*) FILTER (WHERE high_price < low_price
                                   OR high_price < open_price OR high_price < close_price
                                   OR low_price > open_price OR low_price > close_price
                                   OR volume < 0) AS invalid_ohlc
                        FROM daily_candle WHERE code=:code
                        """)
                .bind("code", code)
                .map(
                        (row, metadata) ->
                                new CandleSummary(
                                        row.get("min_date", LocalDate.class),
                                        row.get("max_date", LocalDate.class),
                                        row.get("row_count", Number.class).longValue(),
                                        row.get("duplicates", Number.class).longValue(),
                                        row.get("invalid_ohlc", Number.class).longValue()))
                .one()
                .block(Duration.ofSeconds(15));
    }

    private BackfillSummary backfillSummary(String code) {
        return database.sql(
                        """
                        SELECT status, oldest_synced_date, page_count, candle_count, attempt_count
                        FROM historical_backfill_state WHERE code=:code
                        """)
                .bind("code", code)
                .map(
                        (row, metadata) ->
                                new BackfillSummary(
                                        row.get("status", String.class),
                                        row.get("oldest_synced_date", LocalDate.class),
                                        number(row.get("page_count")),
                                        number(row.get("candle_count")),
                                        number(row.get("attempt_count"))))
                .one()
                .defaultIfEmpty(new BackfillSummary(null, null, 0, 0, 0))
                .block(Duration.ofSeconds(15));
    }

    private long number(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }

    private void assertEqualsOrGreater(long actual, long expected, String message) {
        assertTrue(actual >= expected, message);
    }

    private record CandleSummary(
            LocalDate minDate, LocalDate maxDate, long count, long duplicates, long invalidOhlc) {}

    private record BackfillSummary(
            String status,
            LocalDate oldestDate,
            long pageCount,
            long candleCount,
            long attemptCount) {}
}
