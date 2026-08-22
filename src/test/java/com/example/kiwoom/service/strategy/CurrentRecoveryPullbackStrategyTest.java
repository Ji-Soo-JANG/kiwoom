package com.example.kiwoom.service.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.kiwoom.dto.DailyPriceResponse;
import com.example.kiwoom.dto.MarketRankingItem;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CurrentRecoveryPullbackStrategyTest {
    private final CurrentRecoveryPullbackStrategy strategy = new CurrentRecoveryPullbackStrategy();

    @Test
    void qualifiesOnlyWhenLatestCandleIsCurrentPullback() {
        var result = strategy.analyze(stock(720), pattern(), 240);

        assertThat(result.qualified()).isTrue();
        assertThat(result.matchedConditions()).contains("최신 일봉이 현재 눌림 단계");
    }

    @Test
    void rejectsSamePatternAfterItHasBecomeHistorical() {
        List<DailyPriceResponse> prices = pattern();
        LocalDate next = LocalDate.of(2024, 10, 1);
        for (int i = 0; i < 20; i++) prices.add(day(next.plusDays(i), 700, 1_000));

        var result = strategy.analyze(stock(700), prices, 240);

        assertThat(result.qualified()).isFalse();
        assertThat(result.matchedConditions()).doesNotContain("최신 일봉이 현재 눌림 단계");
    }

    private List<DailyPriceResponse> pattern() {
        List<DailyPriceResponse> prices = new ArrayList<>();
        LocalDate date = LocalDate.of(2024, 1, 1);
        for (int i = 0; i < 30; i++) prices.add(day(date.plusDays(i), 1000, 1_000));
        for (int i = 0; i < 240; i++) {
            long volume = i == 40 || i == 170 ? 3_000 : 1_000;
            prices.add(day(date.plusDays(30L + i), 695 + i % 11, volume));
        }
        prices.add(day(date.plusDays(270), 760, 4_000));
        prices.add(day(date.plusDays(271), 745, 1_500));
        prices.add(day(date.plusDays(272), 720, 1_200));
        return prices;
    }

    private MarketRankingItem stock(long price) {
        return new MarketRankingItem("005930", "테스트", price, 0, 1_000);
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
