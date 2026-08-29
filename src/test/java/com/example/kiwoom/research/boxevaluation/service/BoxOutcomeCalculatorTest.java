package com.example.kiwoom.research.boxevaluation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.kiwoom.dto.StoredDailyCandle;
import java.time.LocalDate;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class BoxOutcomeCalculatorTest {
    @Test
    void calculatesPreRegisteredWindowsAndFirstBarrier() {
        LocalDate cutoff = LocalDate.of(2026, 1, 1);
        var candles =
                IntStream.rangeClosed(1, 20)
                        .mapToObj(
                                day -> {
                                    long close = 100 + day;
                                    return new StoredDailyCandle(
                                            "005930",
                                            cutoff.plusDays(day),
                                            close,
                                            day == 8 ? 112 : close + 1,
                                            close - 1,
                                            close,
                                            1000);
                                })
                        .toList();

        var result = new BoxOutcomeCalculator().calculate(7, "005930", cutoff, candles);

        assertThat(result.policyVersion()).isEqualTo("box-outcome-v1");
        assertThat(result.windows()).extracting(w -> w.tradingDays()).containsExactly(5, 10, 20);
        assertThat(result.firstBarrier()).isEqualTo("TARGET");
        assertThat(result.firstBarrierDate()).isEqualTo(cutoff.plusDays(8));
    }

    @Test
    void rejectsOutcomeBeforeTwentyTradingDays() {
        var candles =
                IntStream.rangeClosed(1, 19)
                        .mapToObj(
                                day ->
                                        new StoredDailyCandle(
                                                "005930",
                                                LocalDate.of(2026, 1, 1).plusDays(day),
                                                100,
                                                101,
                                                99,
                                                100,
                                                1000))
                        .toList();
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () ->
                                new BoxOutcomeCalculator()
                                        .calculate(1, "005930", LocalDate.of(2026, 1, 1), candles))
                .hasMessageContaining("20거래일");
    }
}
