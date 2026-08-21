package com.example.kiwoom.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.kiwoom.dto.DailyPriceResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EventBacktestEngineTest {
    @Test
    void entersOnNextOpenAndUsesStopFirstWhenStopAndTargetBothTouched() {
        List<DailyPriceResponse> prices = pattern();
        LocalDate start = LocalDate.of(2026, 1, 1);
        BacktestConfig config =
                new BacktestConfig(
                        BigDecimal.valueOf(1_000_000), 1, 0.001, 0.002, 0.01, 0.08, 0.15, 20, 60);

        var result =
                new EventBacktestEngine()
                        .run("005930", "테스트", start, start.plusDays(94), prices, config);

        assertThat(result.trades()).hasSize(2);
        assertThat(result.trades().get(0).entryDate()).isEqualTo(start.plusDays(92));
        var trade = result.trades().get(1);
        assertThat(trade.entryDate()).isEqualTo(start.plusDays(94));
        assertThat(trade.exitDate()).isEqualTo(start.plusDays(94));
        assertThat(trade.exitReason()).isEqualTo("STOP_LOSS");
        assertThat(trade.fee()).isPositive();
        assertThat(trade.tax()).isPositive();
        assertThat(trade.slippageCost()).isPositive();
        assertThat(trade.netProfitLoss()).isNegative();
        assertThat(result.finalCapital()).isLessThan(result.initialCapital());
    }

    @Test
    void producesNoTradesWhenThereIsNotEnoughHistory() {
        List<DailyPriceResponse> prices =
                List.of(day(LocalDate.of(2026, 1, 1), 100, 101, 99, 100, 100));
        BacktestConfig config =
                new BacktestConfig(BigDecimal.valueOf(1_000_000), 0.2, 0, 0, 0, 0.08, 0.15, 20, 60);

        var result =
                new EventBacktestEngine()
                        .run(
                                "005930",
                                "테스트",
                                LocalDate.of(2026, 1, 1),
                                LocalDate.of(2026, 1, 2),
                                prices,
                                config);

        assertThat(result.tradeCount()).isZero();
        assertThat(result.finalCapital()).isEqualByComparingTo("1000000.0000");
    }

    private List<DailyPriceResponse> pattern() {
        List<DailyPriceResponse> prices = new ArrayList<>();
        LocalDate start = LocalDate.of(2026, 1, 1);
        for (int index = 0; index < 30; index++) {
            prices.add(day(start.plusDays(index), 180, 200, 175, 180, 100));
        }
        for (int index = 0; index < 60; index++) {
            long volume = index == 15 || index == 42 ? 300 : 100;
            prices.add(day(start.plusDays(30L + index), 103, 110, 100, 105, volume));
        }
        prices.add(day(start.plusDays(90), 106, 121, 106, 120, 500));
        prices.add(day(start.plusDays(91), 118, 122, 114, 117, 120));
        prices.add(day(start.plusDays(92), 116, 118, 113, 115, 100));
        prices.add(day(start.plusDays(93), 100, 102, 98, 100, 100));
        prices.add(day(start.plusDays(94), 100, 120, 80, 95, 100));
        return prices;
    }

    private DailyPriceResponse day(
            LocalDate date, long open, long high, long low, long close, long volume) {
        return new DailyPriceResponse(
                date.format(DateTimeFormatter.BASIC_ISO_DATE), open, high, low, close, volume);
    }
}
