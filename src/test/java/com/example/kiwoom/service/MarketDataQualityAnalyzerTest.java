package com.example.kiwoom.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.kiwoom.dto.StoredDailyCandle;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarketDataQualityAnalyzerTest {
    private final MarketDataQualityAnalyzer analyzer =
            new MarketDataQualityAnalyzer(new MarketCalendarService(""));

    @Test
    void acceptsConsistentAdjustedCandles() {
        var issues =
                analyzer.analyze(
                        List.of(
                                candle("005930", "2026-08-20", 70000, 71000, 69000, 70500, 1000),
                                candle("005930", "2026-08-21", 70500, 72000, 70000, 71500, 1200)));

        assertThat(issues).isEmpty();
    }

    @Test
    void blocksInvalidOhlcNegativeVolumeAndUnverifiedCorporateAction() {
        var issues =
                analyzer.analyze(
                        List.of(
                                candle("005930", "2026-08-20", 70000, 71000, 69000, 70500, 1000),
                                candle("005930", "2026-08-21", 40000, 39000, 41000, 40000, -1)));

        assertThat(issues)
                .extracting(issue -> issue.issueType())
                .contains("INVALID_OHLC", "NEGATIVE_VOLUME", "CORPORATE_ACTION_UNVERIFIED");
        assertThat(issues).allMatch(issue -> issue.blocking());
    }

    @Test
    void warnsWhenSeveralExpectedTradingDaysAreMissing() {
        var issues =
                analyzer.analyze(
                        List.of(
                                candle("005930", "2026-08-10", 70000, 71000, 69000, 70500, 1000),
                                candle("005930", "2026-08-17", 70500, 72000, 70000, 71500, 1200)));

        assertThat(issues)
                .anyMatch(
                        issue ->
                                issue.issueType().equals("MISSING_TRADING_DAYS")
                                        && issue.severity().equals("WARNING"));
    }

    private StoredDailyCandle candle(
            String code, String date, long open, long high, long low, long close, long volume) {
        return new StoredDailyCandle(code, LocalDate.parse(date), open, high, low, close, volume);
    }
}
