package com.example.kiwoom.research.boxevaluation.a1;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Read-only eligibility queries for Discovery A1. */
@Repository
public class A1EligibilityRepository {
    private final DatabaseClient database;

    public A1EligibilityRepository(DatabaseClient database) {
        this.database = database;
    }

    public Mono<List<A1EligibleSymbol>> findEligibleSymbols() {
        return database.sql(
                        """
WITH valid AS (
    SELECT code, trade_date,
           COUNT(*) OVER (
               PARTITION BY code ORDER BY trade_date
               ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS context_count
    FROM daily_candle
    WHERE open_price > 0 AND high_price > 0 AND low_price > 0
      AND close_price > 0 AND volume >= 0
)
SELECT sm.code, sm.market, valid.trade_date
FROM stock_master sm
JOIN historical_backfill_state hs ON hs.code = sm.code
JOIN valid ON valid.code = sm.code AND valid.context_count >= 252
WHERE sm.active = TRUE
  AND sm.market IN ('KOSPI', 'KOSDAQ')
  AND hs.status = 'TARGET_REACHED'
ORDER BY sm.market, sm.code, valid.trade_date
""")
                .map(
                        (row, metadata) ->
                                new CandidateDate(
                                        row.get("code", String.class),
                                        row.get("market", String.class),
                                        row.get("trade_date", LocalDate.class)))
                .all()
                .collectList()
                .map(this::group);
    }

    public Mono<A1EligibilityEvidence> findEvidence(String code, LocalDate cutoffDate) {
        return database.sql(
                        """
                        SELECT hs.status,
                               (SELECT COUNT(*) FROM daily_candle c
                                WHERE c.code=sm.code AND c.trade_date<=:cutoff
                                  AND c.open_price>0 AND c.high_price>0 AND c.low_price>0
                                  AND c.close_price>0 AND c.volume>=0) AS context_count,
                               EXISTS (SELECT 1 FROM daily_candle c
                                WHERE c.code=sm.code AND c.trade_date=:cutoff
                                  AND c.open_price>0 AND c.high_price>0 AND c.low_price>0
                                  AND c.close_price>0 AND c.volume>=0) AS actual_cutoff
                        FROM stock_master sm
                        JOIN historical_backfill_state hs ON hs.code=sm.code
                        WHERE sm.code=:code AND sm.active=TRUE
                          AND sm.market IN ('KOSPI','KOSDAQ')
                        """)
                .bind("code", code)
                .bind("cutoff", cutoffDate)
                .map(
                        (row, metadata) ->
                                new A1EligibilityEvidence(
                                        row.get("status", String.class),
                                        A1Sampler.CONTEXT_CANDLES,
                                        ((Number) row.get("context_count")).intValue(),
                                        Boolean.TRUE.equals(
                                                row.get("actual_cutoff", Boolean.class))))
                .one();
    }

    public Mono<List<A1Sample>> enrichSamples(List<A1Sample> samples, boolean rejectConflicts) {
        return Flux.fromIterable(samples)
                .concatMap(
                        sample ->
                                findEvidence(sample.code(), sample.cutoffDate())
                                        .switchIfEmpty(
                                                Mono.error(
                                                        new A1SamplingException(
                                                                "missing eligibility evidence")))
                                        .map(
                                                evidence -> {
                                                    if (rejectConflicts
                                                            && hasEvidence(sample)
                                                            && !matches(sample, evidence)) {
                                                        throw new A1SamplingException(
                                                                "conflicting eligibility evidence");
                                                    }
                                                    if (!"TARGET_REACHED"
                                                                    .equals(
                                                                            evidence
                                                                                    .historicalBackfillStatus())
                                                            || evidence.actualContextCandleCount()
                                                                    < A1Sampler.CONTEXT_CANDLES
                                                            || !evidence
                                                                    .cutoffIsActualTradingDate()) {
                                                        throw new A1SamplingException(
                                                                "sample is not currently eligible");
                                                    }
                                                    return sample.withEligibilityEvidence(evidence);
                                                }))
                .collectList();
    }

    private boolean hasEvidence(A1Sample sample) {
        return sample.historicalBackfillStatus() != null
                || sample.minimumContextCandles() != 0
                || sample.actualContextCandleCount() != 0
                || sample.cutoffIsActualTradingDate();
    }

    private boolean matches(A1Sample sample, A1EligibilityEvidence evidence) {
        return java.util.Objects.equals(
                        sample.historicalBackfillStatus(), evidence.historicalBackfillStatus())
                && sample.minimumContextCandles() == evidence.minimumContextCandles()
                && sample.actualContextCandleCount() == evidence.actualContextCandleCount()
                && sample.cutoffIsActualTradingDate() == evidence.cutoffIsActualTradingDate();
    }

    private List<A1EligibleSymbol> group(List<CandidateDate> rows) {
        Map<String, A1EligibleSymbolBuilder> grouped = new LinkedHashMap<>();
        for (CandidateDate row : rows) {
            grouped.computeIfAbsent(
                            row.code(),
                            ignored -> new A1EligibleSymbolBuilder(row.code(), row.market()))
                    .dates
                    .add(row.date());
        }
        return grouped.values().stream().map(A1EligibleSymbolBuilder::build).toList();
    }

    private record CandidateDate(String code, String market, LocalDate date) {}

    private static final class A1EligibleSymbolBuilder {
        private final String code;
        private final String market;
        private final List<LocalDate> dates = new ArrayList<>();

        private A1EligibleSymbolBuilder(String code, String market) {
            this.code = code;
            this.market = market;
        }

        private A1EligibleSymbol build() {
            return new A1EligibleSymbol(code, market, dates);
        }
    }
}
