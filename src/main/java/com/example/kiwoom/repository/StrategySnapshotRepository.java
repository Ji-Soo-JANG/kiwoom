package com.example.kiwoom.repository;

import com.example.kiwoom.dto.StrategyCandidate;
import com.example.kiwoom.dto.StrategyScanResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class StrategySnapshotRepository {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final DatabaseClient database;
    private final ObjectMapper objectMapper;

    public StrategySnapshotRepository(DatabaseClient database, ObjectMapper objectMapper) {
        this.database = database;
        this.objectMapper = objectMapper;
    }

    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<StrategyScanResponse> save(
            String strategyVersion,
            int boxRangeDays,
            int scannedCount,
            String scope,
            LocalDate dataAsOf,
            List<StrategyCandidate> allCandidates,
            List<StrategyCandidate> displayedCandidates) {
        DatabaseClient.GenericExecuteSpec insert =
                database.sql(
                                """
                        INSERT INTO strategy_scan(
                            strategy_version, box_range_days, scanned_count, scope, data_as_of)
                        VALUES (:strategyVersion, :boxRangeDays, :scannedCount, :scope, :dataAsOf)
                        """)
                        .bind("strategyVersion", strategyVersion)
                        .bind("boxRangeDays", boxRangeDays)
                        .bind("scannedCount", scannedCount)
                        .bind("scope", scope);
        insert =
                dataAsOf == null
                        ? insert.bindNull("dataAsOf", LocalDate.class)
                        : insert.bind("dataAsOf", dataAsOf);

        return insert.filter(statement -> statement.returnGeneratedValues("id"))
                .map(row -> ((Number) row.get("id")).longValue())
                .one()
                .flatMap(
                        scanId ->
                                Flux.fromIterable(allCandidates)
                                        .index()
                                        .concatMap(
                                                tuple ->
                                                        saveCandidate(
                                                                scanId,
                                                                tuple.getT1() + 1,
                                                                tuple.getT2()))
                                        .then(
                                                Mono.fromSupplier(
                                                        () ->
                                                                new StrategyScanResponse(
                                                                        scanId,
                                                                        strategyVersion,
                                                                        boxRangeDays,
                                                                        displayedCandidates,
                                                                        scannedCount,
                                                                        scope,
                                                                        dataAsOf,
                                                                        Instant.now()))));
    }

    public Mono<StrategyScanResponse> findLatest() {
        return database.sql(
                        """
                SELECT id, strategy_version, box_range_days, scanned_count, scope, data_as_of, created_at
                FROM strategy_scan
                ORDER BY created_at DESC, id DESC
                LIMIT 1
                """)
                .map(
                        row ->
                                new ScanRow(
                                        ((Number) row.get("id")).longValue(),
                                        row.get("strategy_version", String.class),
                                        ((Number) row.get("box_range_days")).intValue(),
                                        ((Number) row.get("scanned_count")).intValue(),
                                        row.get("scope", String.class),
                                        row.get("data_as_of", LocalDate.class),
                                        instant(row.get("created_at"))))
                .one()
                .flatMap(
                        scan ->
                                findCandidates(scan.id(), 30)
                                        .collectList()
                                        .map(
                                                candidates ->
                                                        new StrategyScanResponse(
                                                                scan.id(),
                                                                scan.strategyVersion(),
                                                                scan.boxRangeDays(),
                                                                candidates,
                                                                scan.scannedCount(),
                                                                scan.scope(),
                                                                scan.dataAsOf(),
                                                                scan.createdAt())));
    }

    private Mono<Void> saveCandidate(long scanId, long rank, StrategyCandidate candidate) {
        return database.sql(
                        """
                INSERT INTO strategy_candidate_snapshot(
                    scan_id, rank_no, code, name, current_price, score, qualified,
                    drawdown_rate, box_range_rate, volume_spike_count, breakout_rate,
                    pullback_rate, matched_conditions)
                VALUES (:scanId, :rank, :code, :name, :currentPrice, :score, :qualified,
                    :drawdownRate, :boxRangeRate, :volumeSpikeCount, :breakoutRate,
                    :pullbackRate, :matchedConditions)
                """)
                .bind("scanId", scanId)
                .bind("rank", rank)
                .bind("code", candidate.code())
                .bind("name", candidate.name())
                .bind("currentPrice", candidate.currentPrice())
                .bind("score", candidate.score())
                .bind("qualified", candidate.qualified())
                .bind("drawdownRate", candidate.drawdownRate())
                .bind("boxRangeRate", candidate.boxRangeRate())
                .bind("volumeSpikeCount", candidate.volumeSpikeCount())
                .bind("breakoutRate", candidate.breakoutRate())
                .bind("pullbackRate", candidate.pullbackRate())
                .bind("matchedConditions", writeConditions(candidate.matchedConditions()))
                .fetch()
                .rowsUpdated()
                .then();
    }

    private Flux<StrategyCandidate> findCandidates(long scanId, int limit) {
        return database.sql(
                        """
                SELECT code, name, current_price, score, qualified, drawdown_rate,
                       box_range_rate, volume_spike_count, breakout_rate, pullback_rate,
                       matched_conditions
                FROM strategy_candidate_snapshot
                WHERE scan_id = :scanId
                ORDER BY rank_no
                LIMIT :limit
                """)
                .bind("scanId", scanId)
                .bind("limit", limit)
                .map(
                        row ->
                                new StrategyCandidate(
                                        row.get("code", String.class),
                                        row.get("name", String.class),
                                        ((Number) row.get("current_price")).longValue(),
                                        ((Number) row.get("score")).intValue(),
                                        Boolean.TRUE.equals(row.get("qualified", Boolean.class)),
                                        ((Number) row.get("drawdown_rate")).doubleValue(),
                                        ((Number) row.get("box_range_rate")).doubleValue(),
                                        ((Number) row.get("volume_spike_count")).intValue(),
                                        ((Number) row.get("breakout_rate")).doubleValue(),
                                        ((Number) row.get("pullback_rate")).doubleValue(),
                                        readConditions(
                                                row.get("matched_conditions", String.class))))
                .all();
    }

    private String writeConditions(List<String> conditions) {
        try {
            return objectMapper.writeValueAsString(conditions);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("전략 조건을 저장하지 못했습니다.", error);
        }
    }

    private List<String> readConditions(String value) {
        try {
            return objectMapper.readValue(value, STRING_LIST);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("저장된 전략 조건을 읽지 못했습니다.", error);
        }
    }

    private Instant instant(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        return Instant.parse(value.toString());
    }

    private record ScanRow(
            long id,
            String strategyVersion,
            int boxRangeDays,
            int scannedCount,
            String scope,
            LocalDate dataAsOf,
            Instant createdAt) {}
}
