package com.example.kiwoom.service.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.kiwoom.dto.DailyPriceResponse;
import com.example.kiwoom.dto.MarketRankingItem;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MultiPeriodRecoveryPullbackStrategyTest {
    private final MultiPeriodRecoveryPullbackStrategy strategy =
            new MultiPeriodRecoveryPullbackStrategy();

    @Test
    void findsSharpDropLongBasePartialRecoveryAndPullbackFromStoredDailyCandles() {
        List<DailyPriceResponse> prices = new ArrayList<>();
        LocalDate date = LocalDate.of(2024, 1, 1);
        for (int i = 0; i < 30; i++) prices.add(day(date.plusDays(i), 1000, 1_000));
        for (int i = 0; i < 240; i++) {
            long close = 695 + i % 11;
            long volume = i == 40 || i == 170 ? 3_000 : 1_000;
            prices.add(day(date.plusDays(30L + i), close, volume));
        }
        prices.add(day(date.plusDays(270), 760, 4_000));
        prices.add(day(date.plusDays(271), 745, 1_500));
        prices.add(day(date.plusDays(272), 720, 1_200));

        var result =
                strategy.analyze(
                        new MarketRankingItem("005930", "테스트", 720, 0, 1_200), prices, 240);

        assertThat(result.qualified()).as("result=%s", result).isTrue();
        assertThat(result.score()).isGreaterThanOrEqualTo(75);
        assertThat(result.matchedConditions())
                .anyMatch(condition -> condition.contains("240거래일 장기 박스권"))
                .anyMatch(condition -> condition.contains("이전 낙폭"))
                .anyMatch(condition -> condition.contains("눌림목"));
    }

    @Test
    void rejectsWhenHistoryCannotCoverMinimumBaseWindow() {
        var result =
                strategy.analyze(
                        new MarketRankingItem("005930", "테스트", 100, 0, 100),
                        List.of(day(LocalDate.of(2026, 1, 1), 100, 100)),
                        60);

        assertThat(result.qualified()).isFalse();
        assertThat(result.matchedConditions()).containsExactly("장기 전략 분석용 일봉 부족 (현재 1개)");
    }

    private DailyPriceResponse day(LocalDate date, long close, long volume) {
        return new DailyPriceResponse(
                date.format(DateTimeFormatter.BASIC_ISO_DATE),
                close,
                close + 2,
                close - 2,
                close,
                volume);
    }
}
