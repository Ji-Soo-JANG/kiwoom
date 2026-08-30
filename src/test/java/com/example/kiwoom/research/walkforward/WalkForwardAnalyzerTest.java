package com.example.kiwoom.research.walkforward;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.kiwoom.dto.DailyPriceResponse;
import com.example.kiwoom.research.backtest.BacktestConfig;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WalkForwardAnalyzerTest {
    private final WalkForwardAnalyzer analyzer = new WalkForwardAnalyzer();
    private final BacktestConfig config =
            new BacktestConfig(
                    BigDecimal.valueOf(1_000_000), 0.2, 0.001, 0.002, 0.001, 0.08, 0.15, 20, 60);

    @Test
    void createsStrictlyOrderedTrainingAndValidationFolds() {
        LocalDate start = LocalDate.of(2025, 1, 1);
        List<DailyPriceResponse> prices = flatPrices(start, 220);

        var report =
                analyzer.analyze(
                        "005930", "테스트", start, start.plusDays(219), prices, config, 100, 40, 40);

        assertThat(report.foldCount()).isEqualTo(3);
        assertThat(report.folds())
                .allSatisfy(
                        fold -> assertThat(fold.trainingEnd()).isBefore(fold.validationStart()));
        assertThat(report.folds().get(0).validationStart()).isEqualTo(start.plusDays(100));
        assertThat(report.folds().get(1).trainingStart()).isEqualTo(start.plusDays(40));
        assertThat(report.validationTradeCount()).isZero();
        assertThat(report.passed()).isFalse();
    }

    @Test
    void rejectsPeriodWithoutOneCompleteFold() {
        LocalDate start = LocalDate.of(2025, 1, 1);
        List<DailyPriceResponse> prices = flatPrices(start, 100);

        assertThatThrownBy(
                        () ->
                                analyzer.analyze(
                                        "005930",
                                        "테스트",
                                        start,
                                        start.plusDays(99),
                                        prices,
                                        config,
                                        80,
                                        40,
                                        40))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("거래일 데이터가 부족");
    }

    private List<DailyPriceResponse> flatPrices(LocalDate start, int days) {
        List<DailyPriceResponse> prices = new ArrayList<>();
        for (int index = 0; index < days; index++) {
            prices.add(
                    new DailyPriceResponse(
                            start.plusDays(index).format(DateTimeFormatter.BASIC_ISO_DATE),
                            100,
                            101,
                            99,
                            100,
                            1000));
        }
        return prices;
    }
}
