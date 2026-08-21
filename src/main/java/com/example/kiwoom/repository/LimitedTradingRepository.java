package com.example.kiwoom.repository;

import com.example.kiwoom.dto.LimitedTradeCandidate;
import com.example.kiwoom.dto.PerformanceSampleRequest;
import com.example.kiwoom.dto.TradeCandidateRequest;
import com.example.kiwoom.dto.TradingPerformanceStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class LimitedTradingRepository {
    private final DatabaseClient database;

    public LimitedTradingRepository(DatabaseClient database) {
        this.database = database;
    }

    public Mono<LimitedTradeCandidate> create(TradeCandidateRequest request) {
        return findBySignalId(request.signalId())
                .switchIfEmpty(
                        database.sql(
                                        """
                INSERT INTO limited_trade_candidate(signal_id, code, reason, reference_price,
                    suggested_quantity, expires_at) VALUES (:signal, :code, :reason, :price, :quantity, :expires)
                """)
                                .bind("signal", request.signalId())
                                .bind("code", request.code())
                                .bind("reason", request.reason())
                                .bind("price", request.referencePrice())
                                .bind("quantity", request.suggestedQuantity())
                                .bind("expires", request.expiresAt())
                                .filter(statement -> statement.returnGeneratedValues("id"))
                                .map(row -> ((Number) row.get("id")).longValue())
                                .one()
                                .flatMap(this::findById));
    }

    public Mono<LimitedTradeCandidate> findById(long id) {
        return select(" WHERE id=:value", id);
    }

    private Mono<LimitedTradeCandidate> findBySignalId(String id) {
        return select(" WHERE signal_id=:value", id);
    }

    public Flux<LimitedTradeCandidate> findAll() {
        return database.sql(BASE + " ORDER BY created_at DESC").map(this::map).all();
    }

    public Mono<LimitedTradeCandidate> approve(long id, String user) {
        return database.sql(
                        """
                UPDATE limited_trade_candidate SET status='APPROVED', approved_by=:user,
                    approved_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP
                WHERE id=:id AND status IN ('PENDING', 'APPROVED') AND expires_at > CURRENT_TIMESTAMP
                """)
                .bind("user", user)
                .bind("id", id)
                .fetch()
                .rowsUpdated()
                .flatMap(rows -> rows == 1 ? findById(id) : Mono.empty());
    }

    public Mono<LimitedTradeCandidate> linkOrder(long id, long orderId) {
        return database.sql(
                        "UPDATE limited_trade_candidate SET status='EXECUTED', order_id=:orderId, updated_at=CURRENT_TIMESTAMP WHERE id=:id AND status='APPROVED'")
                .bind("orderId", orderId)
                .bind("id", id)
                .fetch()
                .rowsUpdated()
                .then(findById(id));
    }

    public Mono<Void> resetPending(long id) {
        return database.sql(
                        "UPDATE limited_trade_candidate SET status='PENDING', approved_by=NULL, approved_at=NULL, updated_at=CURRENT_TIMESTAMP WHERE id=:id AND status='APPROVED' AND order_id IS NULL")
                .bind("id", id)
                .then();
    }

    public Mono<LimitedTradeCandidate> reject(long id) {
        return database.sql(
                        "UPDATE limited_trade_candidate SET status='REJECTED', updated_at=CURRENT_TIMESTAMP WHERE id=:id AND status='PENDING'")
                .bind("id", id)
                .fetch()
                .rowsUpdated()
                .then(findById(id));
    }

    public Mono<Void> addPerformance(PerformanceSampleRequest request, BigDecimal slippage) {
        var query =
                database.sql(
                                """
                INSERT INTO trading_performance_sample(order_id, code, expected_price, actual_price,
                    net_return_rate, slippage_rate) VALUES (:orderId, :code, :expected, :actual, :net, :slippage)
                """)
                        .bind("code", request.code())
                        .bind("expected", request.expectedPrice())
                        .bind("actual", request.actualPrice())
                        .bind("net", request.netReturnRate())
                        .bind("slippage", slippage);
        query =
                request.orderId() == null
                        ? query.bindNull("orderId", Long.class)
                        : query.bind("orderId", request.orderId());
        return query.then();
    }

    public Mono<TradingPerformanceStatus> performance() {
        return database.sql(
                        """
                SELECT COUNT(*) sample_count, COALESCE(AVG(ABS(slippage_rate)),0) avg_slippage,
                    COALESCE(AVG(net_return_rate),0) avg_return, COALESCE(MAX(ABS(slippage_rate)),0) max_slippage
                FROM trading_performance_sample
                """)
                .map(
                        row ->
                                new TradingPerformanceStatus(
                                        number(row.get("sample_count")).longValue(),
                                        decimal(row.get("avg_slippage")),
                                        decimal(row.get("avg_return")),
                                        decimal(row.get("max_slippage")),
                                        false,
                                        null,
                                        Instant.now()))
                .one();
    }

    private Mono<LimitedTradeCandidate> select(String clause, Object value) {
        return database.sql(BASE + clause).bind("value", value).map(this::map).one();
    }

    private LimitedTradeCandidate map(io.r2dbc.spi.Readable row) {
        Number orderId = row.get("order_id", Number.class);
        return new LimitedTradeCandidate(
                number(row.get("id")).longValue(),
                row.get("signal_id", String.class),
                row.get("code", String.class),
                row.get("reason", String.class),
                decimal(row.get("reference_price")),
                number(row.get("suggested_quantity")).longValue(),
                row.get("status", String.class),
                instant(row.get("expires_at")),
                row.get("approved_by", String.class),
                instantNullable(row.get("approved_at")),
                orderId == null ? null : orderId.longValue(),
                instant(row.get("created_at")),
                instant(row.get("updated_at")));
    }

    private Number number(Object value) {
        return value == null ? 0 : (Number) value;
    }

    private BigDecimal decimal(Object value) {
        return value == null
                ? BigDecimal.ZERO
                : new BigDecimal(value.toString()).setScale(8, RoundingMode.HALF_UP);
    }

    private Instant instant(Object value) {
        return value instanceof Instant i
                ? i
                : value instanceof OffsetDateTime o
                        ? o.toInstant()
                        : Instant.parse(value.toString());
    }

    private Instant instantNullable(Object value) {
        return value == null ? null : instant(value);
    }

    private static final String BASE =
            "SELECT id, signal_id, code, reason, reference_price, suggested_quantity, status, expires_at, approved_by, approved_at, order_id, created_at, updated_at FROM limited_trade_candidate";
}
