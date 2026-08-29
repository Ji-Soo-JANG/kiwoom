package com.example.kiwoom.research.boxevaluation.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.kiwoom.dto.StoredDailyCandle;
import com.example.kiwoom.research.boxevaluation.model.BoxCandidateType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class BoxCandidateGeneratorTest {
    private final BoxCandidateGenerator generator = new BoxCandidateGenerator();

    @Test
    void deterministicallyBuildsNarrowExpandedAndConnectedCandidates() {
        LocalDate start = LocalDate.of(2025, 1, 1);
        List<StoredDailyCandle> candles = connectedPattern(start);

        var first = generator.generate(candles, start.plusDays(67));
        List<StoredDailyCandle> reversed = new ArrayList<>(candles);
        Collections.reverse(reversed);
        var second = generator.generate(reversed, start.plusDays(67));

        assertThat(first).isEqualTo(second);
        assertThat(first.status()).isEqualTo("READY");
        assertThat(first.candidates())
                .extracting(candidate -> candidate.type())
                .containsExactly(
                        BoxCandidateType.NARROW,
                        BoxCandidateType.EXPANDED,
                        BoxCandidateType.CONNECTED);
        assertThat(first.candidates().get(2).startDate()).isEqualTo(start);
        assertThat(first.candidates().get(2).endDate()).isEqualTo(start.plusDays(64));
        assertThat(first.candidates().get(2).features().volumeSpikeCount()).isEqualTo(2);
    }

    @Test
    void cutoffPreventsFutureCandlesFromChangingCandidates() {
        LocalDate start = LocalDate.of(2025, 1, 1);
        LocalDate cutoff = start.plusDays(67);
        List<StoredDailyCandle> original = connectedPattern(start);
        var expected = generator.generate(original, cutoff);

        List<StoredDailyCandle> withExtremeFuture = new ArrayList<>(original);
        withExtremeFuture.add(candle(start.plusDays(68), 10_000, 99_000));
        withExtremeFuture.add(candle(start.plusDays(69), 1, 1));

        assertThat(generator.generate(withExtremeFuture, cutoff)).isEqualTo(expected);
        assertThat(expected.dataAsOf()).isEqualTo(cutoff);
    }

    @Test
    void reportsInsufficientInsteadOfInventingCandidate() {
        LocalDate start = LocalDate.of(2025, 1, 1);
        List<StoredDailyCandle> candles = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            candles.add(candle(start.plusDays(index), 100, 1_000));
        }

        var result = generator.generate(candles, start.plusDays(9));

        assertThat(result.status()).isEqualTo("INSUFFICIENT");
        assertThat(result.candidates()).isEmpty();
    }

    @Test
    void rejectsMixedCodesAndDuplicateTradingDays() {
        LocalDate day = LocalDate.of(2025, 1, 1);
        List<StoredDailyCandle> mixed = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            String code = index == 19 ? "000660" : "005930";
            mixed.add(candle(code, day.plusDays(index), 100, 1_000));
        }
        assertThatThrownBy(() -> generator.generate(mixed, day.plusDays(30)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same code");

        List<StoredDailyCandle> duplicate = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            duplicate.add(candle(day.plusDays(index), 100, 1_000));
        }
        duplicate.add(candle(day.plusDays(19), 100, 1_000));
        assertThatThrownBy(() -> generator.generate(duplicate, day.plusDays(30)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate trade date");
    }

    private List<StoredDailyCandle> connectedPattern(LocalDate start) {
        List<StoredDailyCandle> candles = new ArrayList<>();
        for (int index = 0; index < 22; index++) {
            candles.add(candle(start.plusDays(index), 100 + index % 3, index == 7 ? 3_000 : 1_000));
        }
        candles.add(candle(start.plusDays(22), 140, 1_100));
        candles.add(candle(start.plusDays(23), 142, 1_100));
        candles.add(candle(start.plusDays(24), 138, 1_100));
        for (int index = 25; index < 65; index++) {
            candles.add(
                    candle(start.plusDays(index), 101 + index % 3, index == 42 ? 3_200 : 1_000));
        }
        candles.add(candle(start.plusDays(65), 115, 4_000));
        candles.add(candle(start.plusDays(66), 112, 1_200));
        candles.add(candle(start.plusDays(67), 110, 1_000));
        return candles;
    }

    private StoredDailyCandle candle(LocalDate date, long close, long volume) {
        return candle("005930", date, close, volume);
    }

    private StoredDailyCandle candle(String code, LocalDate date, long close, long volume) {
        return new StoredDailyCandle(code, date, close, close + 1, close - 1, close, volume);
    }
}
