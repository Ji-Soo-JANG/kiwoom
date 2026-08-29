package com.example.kiwoom.research.boxevaluation.service;

import com.example.kiwoom.dto.StoredDailyCandle;
import com.example.kiwoom.research.boxevaluation.dto.BoxEvaluationOutcome;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class BoxOutcomeCalculator {
    public static final String POLICY_VERSION = "box-outcome-v1";
    private static final int[] HORIZONS = {5, 10, 20};

    public BoxEvaluationOutcome calculate(
            long evaluationId, String code, LocalDate cutoff, List<StoredDailyCandle> future) {
        List<StoredDailyCandle> candles =
                future.stream()
                        .filter(c -> c.tradeDate().isAfter(cutoff))
                        .sorted(java.util.Comparator.comparing(StoredDailyCandle::tradeDate))
                        .toList();
        if (candles.size() < 20) throw new IllegalStateException("20거래일의 미래 일봉이 아직 필요합니다.");
        long entry = candles.getFirst().closePrice();
        List<BoxEvaluationOutcome.Window> windows = new ArrayList<>();
        for (int horizon : HORIZONS) {
            List<StoredDailyCandle> range = candles.subList(0, horizon);
            long high = range.stream().mapToLong(StoredDailyCandle::highPrice).max().orElse(entry);
            long low = range.stream().mapToLong(StoredDailyCandle::lowPrice).min().orElse(entry);
            windows.add(
                    new BoxEvaluationOutcome.Window(
                            horizon,
                            range.getLast().tradeDate(),
                            rate(range.getLast().closePrice(), entry),
                            rate(high, entry),
                            rate(low, entry)));
        }
        String barrier = "NONE";
        LocalDate barrierDate = null;
        for (StoredDailyCandle candle : candles.subList(0, 20)) {
            boolean target = candle.highPrice() >= entry * 1.10;
            boolean invalid = candle.lowPrice() <= entry * 0.95;
            if (target || invalid) {
                barrier = target && invalid ? "AMBIGUOUS" : target ? "TARGET" : "INVALIDATION";
                barrierDate = candle.tradeDate();
                break;
            }
        }
        return new BoxEvaluationOutcome(
                POLICY_VERSION,
                evaluationId,
                code,
                cutoff,
                candles.getFirst().tradeDate(),
                entry,
                windows,
                barrier,
                barrierDate);
    }

    private double rate(long value, long base) {
        return Math.round(((double) value / base - 1) * 1_000_000d) / 1_000_000d;
    }
}
