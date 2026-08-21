package com.example.kiwoom.repository;

import com.example.kiwoom.dto.MarketDataQualityIssue;
import com.example.kiwoom.dto.MarketDataQualityReport;
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
public class MarketDataQualityRepository {
    private final DatabaseClient database;

    public MarketDataQualityRepository(DatabaseClient database) {
        this.database = database;
    }

    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<MarketDataQualityReport> save(
            String policyVersion,
            int stockCount,
            long candleCount,
            List<MarketDataQualityIssue> issues) {
        int blocking = (int) issues.stream().filter(MarketDataQualityIssue::blocking).count();
        int warnings = issues.size() - blocking;
        return database.sql(
                        """
                INSERT INTO market_data_quality_run(
                    policy_version, stock_count, candle_count, blocking_issue_count, warning_issue_count)
                VALUES (:policyVersion, :stockCount, :candleCount, :blocking, :warnings)
                """)
                .bind("policyVersion", policyVersion)
                .bind("stockCount", stockCount)
                .bind("candleCount", candleCount)
                .bind("blocking", blocking)
                .bind("warnings", warnings)
                .filter(statement -> statement.returnGeneratedValues("id"))
                .map(row -> ((Number) row.get("id")).longValue())
                .one()
                .flatMap(
                        runId ->
                                Flux.fromIterable(issues)
                                        .concatMap(issue -> saveIssue(runId, issue))
                                        .then(
                                                Mono.just(
                                                        new MarketDataQualityReport(
                                                                runId,
                                                                policyVersion,
                                                                stockCount,
                                                                candleCount,
                                                                blocking,
                                                                warnings,
                                                                issues.stream().limit(100).toList(),
                                                                Instant.now()))));
    }

    public Mono<MarketDataQualityReport> findLatest() {
        return database.sql(
                        """
                SELECT id, policy_version, stock_count, candle_count, blocking_issue_count,
                       warning_issue_count, created_at
                FROM market_data_quality_run ORDER BY created_at DESC, id DESC LIMIT 1
                """)
                .map(
                        row ->
                                new Run(
                                        ((Number) row.get("id")).longValue(),
                                        row.get("policy_version", String.class),
                                        ((Number) row.get("stock_count")).intValue(),
                                        ((Number) row.get("candle_count")).longValue(),
                                        ((Number) row.get("blocking_issue_count")).intValue(),
                                        ((Number) row.get("warning_issue_count")).intValue(),
                                        instant(row.get("created_at"))))
                .one()
                .flatMap(
                        run ->
                                findIssues(run.id())
                                        .collectList()
                                        .map(
                                                issues ->
                                                        new MarketDataQualityReport(
                                                                run.id(),
                                                                run.policyVersion(),
                                                                run.stockCount(),
                                                                run.candleCount(),
                                                                run.blocking(),
                                                                run.warnings(),
                                                                issues,
                                                                run.createdAt())));
    }

    private Mono<Void> saveIssue(long runId, MarketDataQualityIssue issue) {
        DatabaseClient.GenericExecuteSpec insert =
                database.sql(
                                """
                        INSERT INTO market_data_quality_issue(
                            run_id, code, trade_date, issue_type, severity, detail)
                        VALUES (:runId, :code, :tradeDate, :type, :severity, :detail)
                        """)
                        .bind("runId", runId)
                        .bind("code", issue.code())
                        .bind("type", issue.issueType())
                        .bind("severity", issue.severity())
                        .bind("detail", issue.detail());
        insert =
                issue.tradeDate() == null
                        ? insert.bindNull("tradeDate", LocalDate.class)
                        : insert.bind("tradeDate", issue.tradeDate());
        return insert.fetch().rowsUpdated().then();
    }

    private Flux<MarketDataQualityIssue> findIssues(long runId) {
        return database.sql(
                        """
                SELECT code, trade_date, issue_type, severity, detail
                FROM market_data_quality_issue WHERE run_id = :runId
                ORDER BY CASE WHEN severity = 'BLOCKING' THEN 0 ELSE 1 END, code, trade_date
                LIMIT 100
                """)
                .bind("runId", runId)
                .map(
                        row ->
                                new MarketDataQualityIssue(
                                        row.get("code", String.class),
                                        row.get("trade_date", LocalDate.class),
                                        row.get("issue_type", String.class),
                                        row.get("severity", String.class),
                                        row.get("detail", String.class)))
                .all();
    }

    private Instant instant(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime dateTime) return dateTime.toInstant();
        return Instant.parse(value.toString());
    }

    private record Run(
            long id,
            String policyVersion,
            int stockCount,
            long candleCount,
            int blocking,
            int warnings,
            Instant createdAt) {}
}
