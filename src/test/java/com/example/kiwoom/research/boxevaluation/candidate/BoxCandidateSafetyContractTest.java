package com.example.kiwoom.research.boxevaluation.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.kiwoom.dto.StoredDailyCandle;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Independent PH1-08 safety contract. It intentionally treats the generator as a black box. */
class BoxCandidateSafetyContractTest {
    private final BoxCandidateGenerator generator = new BoxCandidateGenerator();

    @Test
    void arbitraryFutureDataCannotAffectBlindCandidateOutput() {
        LocalDate start = LocalDate.of(2024, 1, 2);
        LocalDate cutoff = start.plusDays(89);
        List<StoredDailyCandle> knownAtCutoff = stableHistory(start, 90);
        var baseline = generator.generate(knownAtCutoff, cutoff);
        Random random = new Random(6_202_608L);

        for (int run = 0; run < 50; run++) {
            List<StoredDailyCandle> contaminated = new ArrayList<>(knownAtCutoff);
            for (int futureDay = 1; futureDay <= 30; futureDay++) {
                long close = 1 + random.nextInt(1_000_000);
                long volume = random.nextLong(10_000_000_000L);
                contaminated.add(candle(cutoff.plusDays(futureDay), close, volume));
            }
            Collections.shuffle(contaminated, random);

            var result = generator.generate(contaminated, cutoff);

            assertThat(result).as("future contamination run %s", run).isEqualTo(baseline);
            assertThat(result.dataAsOf()).isEqualTo(cutoff);
            assertThat(result.candidates())
                    .allSatisfy(
                            candidate -> {
                                assertThat(candidate.startDate()).isBeforeOrEqualTo(cutoff);
                                assertThat(candidate.endDate()).isBeforeOrEqualTo(cutoff);
                            });
        }
    }

    @Test
    void outputCollectionsCannotBeMutatedAfterGeneration() {
        LocalDate start = LocalDate.of(2024, 1, 2);
        var result = generator.generate(stableHistory(start, 90), start.plusDays(89));

        assertThatThrownBy(() -> result.candidates().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.messages().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.candidates().getFirst().boundaryEvidence().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void cutoffBeforeEveryCandleProducesNoCandidateOrDataAsOf() {
        LocalDate start = LocalDate.of(2024, 1, 2);

        var result = generator.generate(stableHistory(start, 90), start.minusDays(1));

        assertThat(result.status()).isEqualTo("INSUFFICIENT");
        assertThat(result.dataAsOf()).isNull();
        assertThat(result.candidates()).isEmpty();
    }

    private List<StoredDailyCandle> stableHistory(LocalDate start, int days) {
        List<StoredDailyCandle> result = new ArrayList<>();
        for (int index = 0; index < days; index++) {
            long close = 10_000 + (index % 7 - 3) * 25L;
            long volume = index == 22 || index == 61 ? 4_000_000 : 1_000_000;
            result.add(candle(start.plusDays(index), close, volume));
        }
        return result;
    }

    private StoredDailyCandle candle(LocalDate date, long close, long volume) {
        return new StoredDailyCandle("005930", date, close, close + 10, close - 10, close, volume);
    }
}
