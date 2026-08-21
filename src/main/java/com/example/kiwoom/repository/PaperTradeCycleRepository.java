package com.example.kiwoom.repository;

import com.example.kiwoom.dto.PaperTradeCycle;
import com.example.kiwoom.dto.PaperTradeResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class PaperTradeCycleRepository {
    private final DatabaseClient database;

    public PaperTradeCycleRepository(DatabaseClient database) {
        this.database = database;
    }

    public Mono<PaperTradeCycle> open(
            long candidateId, String code, long quantity, long orderId, BigDecimal entryPrice) {
        BigDecimal stop = entryPrice.multiply(new BigDecimal("0.95"));
        BigDecimal take = entryPrice.multiply(new BigDecimal("1.10"));
        return database.sql(
                        """
                INSERT INTO paper_trade_cycle(entry_candidate_id, code, quantity, entry_order_id,
                    entry_price, stop_loss_price, take_profit_price, max_holding_days)
                VALUES (:candidateId, :code, :quantity, :orderId, :entryPrice, :stop, :take, 10)
                ON CONFLICT (entry_candidate_id) DO NOTHING
                """)
                .bind("candidateId", candidateId)
                .bind("code", code)
                .bind("quantity", quantity)
                .bind("orderId", orderId)
                .bind("entryPrice", entryPrice)
                .bind("stop", stop)
                .bind("take", take)
                .fetch()
                .rowsUpdated()
                .then(findByCandidateId(candidateId));
    }

    public Flux<PaperTradeCycle> findAll() {
        return database.sql("SELECT * FROM paper_trade_cycle ORDER BY opened_at DESC")
                .map(this::map)
                .all();
    }

    public Mono<PaperTradeCycle> findById(long id) {
        return database.sql("SELECT * FROM paper_trade_cycle WHERE id = :id")
                .bind("id", id)
                .map(this::map)
                .one();
    }

    public Mono<PaperTradeCycle> findByCandidateId(long id) {
        return database.sql("SELECT * FROM paper_trade_cycle WHERE entry_candidate_id = :id")
                .bind("id", id)
                .map(this::map)
                .one();
    }

    public Flux<PaperTradeCycle> findHoldingByCode(String code) {
        return database.sql(
                        "SELECT * FROM paper_trade_cycle WHERE code = :code AND status = 'HOLDING'")
                .bind("code", code)
                .map(this::map)
                .all();
    }

    public Mono<PaperTradeCycle> requestExit(long id, String reason, BigDecimal price, Instant at) {
        return database.sql(
                        """
                UPDATE paper_trade_cycle SET status='EXIT_PENDING', exit_reason=:reason,
                    exit_trigger_price=:price, exit_requested_at=:at, updated_at=CURRENT_TIMESTAMP
                WHERE id=:id AND status='HOLDING'
                """)
                .bind("reason", reason)
                .bind("price", price)
                .bind("at", at)
                .bind("id", id)
                .fetch()
                .rowsUpdated()
                .flatMap(n -> n == 1 ? findById(id) : Mono.empty());
    }

    public Mono<PaperTradeCycle> close(long id, long orderId) {
        return database.sql(
                        """
                UPDATE paper_trade_cycle SET status='CLOSED', exit_order_id=:orderId,
                    closed_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP
                WHERE id=:id AND status='EXIT_PENDING' AND exit_order_id IS NULL
                """)
                .bind("orderId", orderId)
                .bind("id", id)
                .fetch()
                .rowsUpdated()
                .flatMap(n -> n == 1 ? findById(id) : Mono.empty());
    }

    public Mono<Void> saveResult(PaperTradeResult r) {
        return database.sql(
                        """
                INSERT INTO paper_trade_result(cycle_id, gross_pnl, total_cost, net_pnl,
                    net_return_rate, holding_days, exit_reason, closed_at)
                VALUES (:cycleId,:gross,:cost,:net,:rate,:days,:reason,:closed)
                ON CONFLICT (cycle_id) DO NOTHING
                """)
                .bind("cycleId", r.cycleId())
                .bind("gross", r.grossPnl())
                .bind("cost", r.totalCost())
                .bind("net", r.netPnl())
                .bind("rate", r.netReturnRate())
                .bind("days", r.holdingDays())
                .bind("reason", r.exitReason())
                .bind("closed", r.closedAt())
                .fetch()
                .rowsUpdated()
                .then();
    }

    public Flux<PaperTradeResult> results() {
        return database.sql("SELECT * FROM paper_trade_result ORDER BY closed_at")
                .map(
                        (row, meta) ->
                                new PaperTradeResult(
                                        ((Number) row.get("cycle_id")).longValue(),
                                        row.get("gross_pnl", BigDecimal.class),
                                        row.get("total_cost", BigDecimal.class),
                                        row.get("net_pnl", BigDecimal.class),
                                        row.get("net_return_rate", BigDecimal.class),
                                        ((Number) row.get("holding_days")).intValue(),
                                        row.get("exit_reason", String.class),
                                        instant(row.get("closed_at"))))
                .all();
    }

    private PaperTradeCycle map(io.r2dbc.spi.Row row, io.r2dbc.spi.RowMetadata ignored) {
        Number exitOrder = row.get("exit_order_id", Number.class);
        return new PaperTradeCycle(
                ((Number) row.get("id")).longValue(),
                ((Number) row.get("entry_candidate_id")).longValue(),
                row.get("code", String.class),
                ((Number) row.get("quantity")).longValue(),
                ((Number) row.get("entry_order_id")).longValue(),
                row.get("entry_price", BigDecimal.class),
                row.get("stop_loss_price", BigDecimal.class),
                row.get("take_profit_price", BigDecimal.class),
                ((Number) row.get("max_holding_days")).intValue(),
                row.get("status", String.class),
                row.get("exit_reason", String.class),
                row.get("exit_trigger_price", BigDecimal.class),
                exitOrder == null ? null : exitOrder.longValue(),
                instant(row.get("opened_at")),
                instantOrNull(row.get("exit_requested_at")),
                instantOrNull(row.get("closed_at")));
    }

    private Instant instant(Object value) {
        return value instanceof Instant i
                ? i
                : value instanceof OffsetDateTime o
                        ? o.toInstant()
                        : Instant.parse(value.toString());
    }

    private Instant instantOrNull(Object value) {
        return value == null ? null : instant(value);
    }
}
