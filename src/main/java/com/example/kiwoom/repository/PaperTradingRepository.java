package com.example.kiwoom.repository;

import com.example.kiwoom.dto.OrderSide;
import com.example.kiwoom.dto.OrderStatus;
import com.example.kiwoom.dto.PaperAccountStatus;
import com.example.kiwoom.dto.PaperOrderRequest;
import com.example.kiwoom.dto.PaperPosition;
import com.example.kiwoom.dto.TradingMode;
import com.example.kiwoom.dto.TradingOrder;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class PaperTradingRepository {
    private final DatabaseClient database;

    public PaperTradingRepository(DatabaseClient database) {
        this.database = database;
    }

    public Mono<PaperAccountStatus> initializeAccount(
            BigDecimal initialCash, LocalDate tradingDay) {
        return findAccount()
                .switchIfEmpty(
                        database.sql(
                                        """
                                INSERT INTO paper_account(
                                    id, initial_cash, cash, peak_equity, trading_day, day_start_equity)
                                VALUES (1, :cash, :cash, :cash, :tradingDay, :cash)
                                """)
                                .bind("cash", initialCash)
                                .bind("tradingDay", tradingDay)
                                .fetch()
                                .rowsUpdated()
                                .then(findAccount())
                                .onErrorResume(
                                        DuplicateKeyException.class, ignored -> findAccount()));
    }

    public Mono<PaperAccountStatus> findAccount() {
        return database.sql(
                        """
                SELECT initial_cash, cash, peak_equity, trading_day, day_start_equity,
                       kill_switch_active, kill_switch_reason, kill_switch_activated_at
                FROM paper_account WHERE id = 1
                """)
                .map(
                        row ->
                                new PaperAccountStatus(
                                        row.get("initial_cash", BigDecimal.class),
                                        row.get("cash", BigDecimal.class),
                                        row.get("peak_equity", BigDecimal.class),
                                        row.get("trading_day", LocalDate.class),
                                        row.get("day_start_equity", BigDecimal.class),
                                        Boolean.TRUE.equals(
                                                row.get("kill_switch_active", Boolean.class)),
                                        row.get("kill_switch_reason", String.class),
                                        instantOrNull(row.get("kill_switch_activated_at"))))
                .one();
    }

    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<TradingOrder> createIdempotent(PaperOrderRequest request, TradingMode mode) {
        return findByDecisionId(request.decisionId())
                .switchIfEmpty(
                        database.sql(
                                        """
                                INSERT INTO trading_order(
                                    decision_id, mode, code, side, requested_quantity,
                                    requested_price, status)
                                VALUES (:decisionId, :mode, :code, :side, :quantity,
                                    :price, 'CREATED')
                                """)
                                .bind("decisionId", request.decisionId())
                                .bind("mode", mode.name())
                                .bind("code", request.code())
                                .bind("side", request.side().name())
                                .bind("quantity", request.quantity())
                                .bind("price", request.price())
                                .filter(statement -> statement.returnGeneratedValues("id"))
                                .map(row -> ((Number) row.get("id")).longValue())
                                .one()
                                .flatMap(
                                        id ->
                                                addEvent(id, null, OrderStatus.CREATED, "주문 생성")
                                                        .then(findById(id)))
                                .onErrorResume(
                                        DuplicateKeyException.class,
                                        ignored -> findByDecisionId(request.decisionId())));
    }

    public Mono<TradingOrder> findByDecisionId(String decisionId) {
        return database.sql("SELECT * FROM trading_order WHERE decision_id = :decisionId")
                .bind("decisionId", decisionId)
                .map((row, metadata) -> mapOrder(row))
                .one();
    }

    public Mono<TradingOrder> findById(long id) {
        return database.sql("SELECT * FROM trading_order WHERE id = :id")
                .bind("id", id)
                .map((row, metadata) -> mapOrder(row))
                .one();
    }

    public Flux<TradingOrder> findAllOrders() {
        return database.sql("SELECT * FROM trading_order ORDER BY id")
                .map((row, metadata) -> mapOrder(row))
                .all();
    }

    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<TradingOrder> transition(long id, OrderStatus from, OrderStatus to, String detail) {
        return database.sql(
                        """
                UPDATE trading_order SET status = :toStatus, updated_at = CURRENT_TIMESTAMP
                WHERE id = :id AND status = :fromStatus
                """)
                .bind("toStatus", to.name())
                .bind("id", id)
                .bind("fromStatus", from.name())
                .fetch()
                .rowsUpdated()
                .flatMap(
                        updated -> {
                            if (updated != 1) {
                                return Mono.error(
                                        new IllegalStateException(
                                                "주문 상태가 이미 변경되어 전이를 적용할 수 없습니다."));
                            }
                            return addEvent(id, from, to, detail).then(findById(id));
                        });
    }

    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<TradingOrder> fillAcknowledged(TradingOrder order) {
        BigDecimal amount =
                order.requestedPrice().multiply(BigDecimal.valueOf(order.requestedQuantity()));
        BigDecimal cashDelta = order.side() == OrderSide.BUY ? amount.negate() : amount;
        return database.sql(
                        """
                INSERT INTO trading_fill(
                    execution_id, order_id, code, side, quantity, price)
                VALUES (:executionId, :orderId, :code, :side, :quantity, :price)
                """)
                .bind("executionId", "PAPER-" + order.decisionId())
                .bind("orderId", order.id())
                .bind("code", order.code())
                .bind("side", order.side().name())
                .bind("quantity", order.requestedQuantity())
                .bind("price", order.requestedPrice())
                .fetch()
                .rowsUpdated()
                .then(applyPosition(order))
                .then(
                        database.sql(
                                        """
                                UPDATE paper_account SET cash = cash + :cashDelta,
                                    updated_at = CURRENT_TIMESTAMP WHERE id = 1
                                """)
                                .bind("cashDelta", cashDelta)
                                .fetch()
                                .rowsUpdated())
                .then(
                        database.sql(
                                        """
                                UPDATE trading_order SET status = 'FILLED',
                                    filled_quantity = requested_quantity,
                                    average_fill_price = requested_price,
                                    updated_at = CURRENT_TIMESTAMP
                                WHERE id = :id AND status = 'ACKNOWLEDGED'
                                """)
                                .bind("id", order.id())
                                .fetch()
                                .rowsUpdated())
                .flatMap(
                        updated ->
                                updated == 1
                                        ? addEvent(
                                                        order.id(),
                                                        OrderStatus.ACKNOWLEDGED,
                                                        OrderStatus.FILLED,
                                                        "PAPER 전량 체결")
                                                .then(findById(order.id()))
                                        : Mono.error(
                                                new IllegalStateException(
                                                        "체결 전에 주문 상태가 변경되었습니다.")));
    }

    public Flux<PaperPosition> findPositions() {
        return database.sql(
                        "SELECT code, quantity, average_price FROM paper_position ORDER BY code")
                .map(
                        row ->
                                new PaperPosition(
                                        row.get("code", String.class),
                                        ((Number) row.get("quantity")).longValue(),
                                        row.get("average_price", BigDecimal.class)))
                .all();
    }

    public Mono<PaperPosition> findPosition(String code) {
        return database.sql(
                        "SELECT code, quantity, average_price FROM paper_position WHERE code = :code")
                .bind("code", code)
                .map(
                        row ->
                                new PaperPosition(
                                        row.get("code", String.class),
                                        ((Number) row.get("quantity")).longValue(),
                                        row.get("average_price", BigDecimal.class)))
                .one();
    }

    public Flux<StoredFill> findAllFills() {
        return database.sql(
                        "SELECT order_id, code, side, quantity, price, fee, tax FROM trading_fill ORDER BY id")
                .map(
                        row ->
                                new StoredFill(
                                        ((Number) row.get("order_id")).longValue(),
                                        row.get("code", String.class),
                                        OrderSide.valueOf(row.get("side", String.class)),
                                        ((Number) row.get("quantity")).longValue(),
                                        row.get("price", BigDecimal.class),
                                        row.get("fee", BigDecimal.class),
                                        row.get("tax", BigDecimal.class)))
                .all();
    }

    public Mono<Void> updateRiskSnapshot(BigDecimal equity, LocalDate tradingDay) {
        return database.sql(
                        """
                UPDATE paper_account SET
                    day_start_equity = CASE WHEN trading_day <> :tradingDay
                        THEN :equity ELSE day_start_equity END,
                    trading_day = :tradingDay,
                    peak_equity = CASE WHEN peak_equity < :equity THEN :equity ELSE peak_equity END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = 1
                """)
                .bind("tradingDay", tradingDay)
                .bind("equity", equity)
                .fetch()
                .rowsUpdated()
                .then();
    }

    public Mono<Void> activateKillSwitch(String reason) {
        return database.sql(
                        """
                UPDATE paper_account SET kill_switch_active = TRUE,
                    kill_switch_reason = :reason,
                    kill_switch_activated_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = 1
                """)
                .bind("reason", reason)
                .fetch()
                .rowsUpdated()
                .then();
    }

    public Mono<Void> resumeKillSwitch(BigDecimal equity, LocalDate tradingDay) {
        return database.sql(
                        """
                UPDATE paper_account SET kill_switch_active = FALSE,
                    kill_switch_reason = NULL, kill_switch_activated_at = NULL,
                    peak_equity = :equity, day_start_equity = :equity,
                    trading_day = :tradingDay, updated_at = CURRENT_TIMESTAMP
                WHERE id = 1
                """)
                .bind("equity", equity)
                .bind("tradingDay", tradingDay)
                .fetch()
                .rowsUpdated()
                .then();
    }

    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<TradingOrder> rejectCreated(long id, String reason) {
        return database.sql(
                        """
                UPDATE trading_order SET status = 'REJECTED', rejection_reason = :reason,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = :id AND status = 'CREATED'
                """)
                .bind("reason", reason)
                .bind("id", id)
                .fetch()
                .rowsUpdated()
                .flatMap(
                        updated ->
                                updated == 1
                                        ? addEvent(
                                                        id,
                                                        OrderStatus.CREATED,
                                                        OrderStatus.REJECTED,
                                                        reason)
                                                .then(findById(id))
                                        : Mono.error(
                                                new IllegalStateException(
                                                        "거부 처리 전에 주문 상태가 변경되었습니다.")));
    }

    private Mono<Void> applyPosition(TradingOrder order) {
        return findPosition(order.code())
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(
                        current ->
                                current.isPresent()
                                        ? updatePosition(current.get(), order)
                                        : order.side() == OrderSide.BUY
                                                ? insertPosition(order)
                                                : Mono.error(
                                                        new IllegalArgumentException(
                                                                "매도할 PAPER 보유 수량이 없습니다.")));
    }

    private Mono<Void> updatePosition(PaperPosition current, TradingOrder order) {
        if (order.side() == OrderSide.SELL) {
            long remaining = current.quantity() - order.requestedQuantity();
            if (remaining < 0)
                return Mono.error(new IllegalArgumentException("PAPER 보유 수량을 초과한 매도입니다."));
            if (remaining == 0) {
                return database.sql("DELETE FROM paper_position WHERE code = :code")
                        .bind("code", order.code())
                        .fetch()
                        .rowsUpdated()
                        .then();
            }
            return database.sql(
                            "UPDATE paper_position SET quantity = :quantity, updated_at = CURRENT_TIMESTAMP WHERE code = :code")
                    .bind("quantity", remaining)
                    .bind("code", order.code())
                    .fetch()
                    .rowsUpdated()
                    .then();
        }
        long totalQuantity = current.quantity() + order.requestedQuantity();
        BigDecimal totalCost =
                current.averagePrice()
                        .multiply(BigDecimal.valueOf(current.quantity()))
                        .add(
                                order.requestedPrice()
                                        .multiply(BigDecimal.valueOf(order.requestedQuantity())));
        BigDecimal average =
                totalCost.divide(BigDecimal.valueOf(totalQuantity), 4, RoundingMode.HALF_UP);
        return database.sql(
                        """
                UPDATE paper_position SET quantity = :quantity, average_price = :average,
                    updated_at = CURRENT_TIMESTAMP WHERE code = :code
                """)
                .bind("quantity", totalQuantity)
                .bind("average", average)
                .bind("code", order.code())
                .fetch()
                .rowsUpdated()
                .then();
    }

    private Mono<Void> insertPosition(TradingOrder order) {
        return database.sql(
                        "INSERT INTO paper_position(code, quantity, average_price) VALUES (:code, :quantity, :price)")
                .bind("code", order.code())
                .bind("quantity", order.requestedQuantity())
                .bind("price", order.requestedPrice())
                .fetch()
                .rowsUpdated()
                .then();
    }

    private Mono<Void> addEvent(long orderId, OrderStatus from, OrderStatus to, String detail) {
        DatabaseClient.GenericExecuteSpec insert =
                database.sql(
                                """
                        INSERT INTO trading_order_event(order_id, from_status, to_status, detail)
                        VALUES (:orderId, :fromStatus, :toStatus, :detail)
                        """)
                        .bind("orderId", orderId)
                        .bind("toStatus", to.name())
                        .bind("detail", detail);
        insert =
                from == null
                        ? insert.bindNull("fromStatus", String.class)
                        : insert.bind("fromStatus", from.name());
        return insert.fetch().rowsUpdated().then();
    }

    private TradingOrder mapOrder(io.r2dbc.spi.Row row) {
        return new TradingOrder(
                ((Number) row.get("id")).longValue(),
                row.get("decision_id", String.class),
                TradingMode.valueOf(row.get("mode", String.class)),
                row.get("code", String.class),
                OrderSide.valueOf(row.get("side", String.class)),
                ((Number) row.get("requested_quantity")).longValue(),
                row.get("requested_price", BigDecimal.class),
                OrderStatus.valueOf(row.get("status", String.class)),
                ((Number) row.get("filled_quantity")).longValue(),
                row.get("average_fill_price", BigDecimal.class),
                row.get("rejection_reason", String.class),
                instant(row.get("created_at")),
                instant(row.get("updated_at")));
    }

    private Instant instant(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime offset) return offset.toInstant();
        return Instant.parse(value.toString());
    }

    private Instant instantOrNull(Object value) {
        return value == null ? null : instant(value);
    }

    public record StoredFill(
            long orderId,
            String code,
            OrderSide side,
            long quantity,
            BigDecimal price,
            BigDecimal fee,
            BigDecimal tax) {}
}
