package com.example.kiwoom.repository;

import com.example.kiwoom.dto.DailyPriceResponse;
import com.example.kiwoom.dto.MarketDataSyncStatus;
import com.example.kiwoom.dto.MarketRankingItem;
import com.example.kiwoom.dto.StockSearchResult;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class MarketDataRepository {
    private final DatabaseClient database;

    public MarketDataRepository(DatabaseClient database) {
        this.database = database;
    }

    public Mono<Void> saveStocks(Flux<StockSearchResult> stocks) {
        return stocks.concatMap(this::saveStock).then();
    }

    private Mono<Void> saveStock(StockSearchResult stock) {
        return database.sql(
                        """
                UPDATE stock_master
                SET name = :name, market = :market, product_type = :productType,
                    active = TRUE, updated_at = CURRENT_TIMESTAMP
                WHERE code = :code
                """)
                .bind("code", stock.code())
                .bind("name", stock.name())
                .bind("market", stock.market())
                .bind("productType", stock.productType().name())
                .fetch()
                .rowsUpdated()
                .flatMap(
                        updated ->
                                updated > 0
                                        ? Mono.empty()
                                        : database.sql(
                                                        """
                                                INSERT INTO stock_master(code, name, market, product_type)
                                                VALUES (:code, :name, :market, :productType)
                                                """)
                                                .bind("code", stock.code())
                                                .bind("name", stock.name())
                                                .bind("market", stock.market())
                                                .bind("productType", stock.productType().name())
                                                .fetch()
                                                .rowsUpdated()
                                                .then())
                .onErrorResume(DuplicateKeyException.class, error -> Mono.empty())
                .then();
    }

    public Flux<String> findCodesToSync(int limit) {
        return database.sql(
                        """
                SELECT sm.code
                FROM stock_master sm
                LEFT JOIN market_data_sync_state state ON state.code = sm.code
                WHERE sm.active = TRUE
                ORDER BY CASE WHEN state.status = 'FAILED' THEN 0 WHEN state.code IS NULL THEN 1 ELSE 2 END,
                         state.updated_at NULLS FIRST, sm.code
                LIMIT :limit
                """)
                .bind("limit", limit)
                .map(row -> row.get("code", String.class))
                .all();
    }

    public Mono<Void> saveCandles(String code, Flux<DailyPriceResponse> candles) {
        return candles.concatMap(candle -> saveCandle(code, candle)).then();
    }

    private Mono<Void> saveCandle(String code, DailyPriceResponse candle) {
        LocalDate date =
                LocalDate.parse(
                        candle.getDate(), java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        return database.sql(
                        """
                UPDATE daily_candle
                SET open_price = :open, high_price = :high, low_price = :low,
                    close_price = :close, volume = :volume, updated_at = CURRENT_TIMESTAMP
                WHERE code = :code AND trade_date = :tradeDate
                """)
                .bind("code", code)
                .bind("tradeDate", date)
                .bind("open", candle.getOpenPrice())
                .bind("high", candle.getHighPrice())
                .bind("low", candle.getLowPrice())
                .bind("close", candle.getClosePrice())
                .bind("volume", candle.getVolume())
                .fetch()
                .rowsUpdated()
                .flatMap(
                        updated ->
                                updated > 0
                                        ? Mono.empty()
                                        : database.sql(
                                                        """
                                                INSERT INTO daily_candle(
                                                    code, trade_date, open_price, high_price,
                                                    low_price, close_price, volume)
                                                VALUES (:code, :tradeDate, :open, :high, :low, :close, :volume)
                                                """)
                                                .bind("code", code)
                                                .bind("tradeDate", date)
                                                .bind("open", candle.getOpenPrice())
                                                .bind("high", candle.getHighPrice())
                                                .bind("low", candle.getLowPrice())
                                                .bind("close", candle.getClosePrice())
                                                .bind("volume", candle.getVolume())
                                                .fetch()
                                                .rowsUpdated()
                                                .then())
                .onErrorResume(DuplicateKeyException.class, error -> Mono.empty())
                .then();
    }

    public Mono<Void> markSuccess(String code, LocalDate lastDate) {
        return saveState(code, lastDate, "SUCCESS", null);
    }

    public Mono<Void> markFailure(String code, String message) {
        String safeMessage =
                message == null ? "unknown" : message.substring(0, Math.min(500, message.length()));
        return saveState(code, null, "FAILED", safeMessage);
    }

    private Mono<Void> saveState(String code, LocalDate lastDate, String status, String error) {
        DatabaseClient.GenericExecuteSpec update =
                database.sql(
                                """
                        UPDATE market_data_sync_state
                        SET last_synced_date = COALESCE(:lastDate, last_synced_date), status = :status,
                            error_message = :error, updated_at = CURRENT_TIMESTAMP
                        WHERE code = :code
                        """)
                        .bind("code", code)
                        .bind("status", status);
        update = bindNullable(update, "lastDate", lastDate, LocalDate.class);
        update = bindNullable(update, "error", error, String.class);
        DatabaseClient.GenericExecuteSpec finalUpdate = update;
        return update.fetch()
                .rowsUpdated()
                .flatMap(
                        updated ->
                                updated > 0
                                        ? Mono.empty()
                                        : insertState(code, lastDate, status, error))
                .onErrorResume(
                        DuplicateKeyException.class,
                        ignored -> finalUpdate.fetch().rowsUpdated().then())
                .then();
    }

    private Mono<Void> insertState(String code, LocalDate lastDate, String status, String error) {
        DatabaseClient.GenericExecuteSpec insert =
                database.sql(
                                """
                        INSERT INTO market_data_sync_state(code, last_synced_date, status, error_message)
                        VALUES (:code, :lastDate, :status, :error)
                        """)
                        .bind("code", code)
                        .bind("status", status);
        insert = bindNullable(insert, "lastDate", lastDate, LocalDate.class);
        insert = bindNullable(insert, "error", error, String.class);
        return insert.fetch().rowsUpdated().then();
    }

    private <T> DatabaseClient.GenericExecuteSpec bindNullable(
            DatabaseClient.GenericExecuteSpec spec, String name, T value, Class<T> type) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }

    public Mono<MarketDataSyncStatus> status(
            int processed, int succeeded, int failed, boolean running) {
        return database.sql(
                        """
                SELECT
                    (SELECT COUNT(*) FROM stock_master WHERE active = TRUE) AS stock_count,
                    (SELECT COUNT(*) FROM daily_candle) AS candle_count,
                    (SELECT COUNT(*) FROM market_data_sync_state WHERE status = 'SUCCESS') AS synced_count,
                    (SELECT COUNT(*) FROM market_data_sync_state WHERE status = 'FAILED') AS failed_count,
                    (SELECT MAX(trade_date) FROM daily_candle) AS latest_trade_date
                """)
                .map(
                        row ->
                                new MarketDataSyncStatus(
                                        number(row.get("stock_count")),
                                        number(row.get("candle_count")),
                                        number(row.get("synced_count")),
                                        number(row.get("failed_count")),
                                        row.get("latest_trade_date", LocalDate.class),
                                        processed,
                                        succeeded,
                                        failed,
                                        running,
                                        Instant.now()))
                .one();
    }

    public Flux<StockSearchResult> findStocksByCodes(List<String> codes) {
        if (codes.isEmpty()) return Flux.empty();
        String placeholders =
                codes.stream()
                        .map(c -> "'" + c + "'")
                        .collect(java.util.stream.Collectors.joining(","));
        return database.sql(
                        "SELECT code, name, market FROM stock_master WHERE code IN ("
                                + placeholders
                                + ")")
                .map(
                        (row, metadata) ->
                                new StockSearchResult(
                                        row.get("code", String.class),
                                        row.get("name", String.class),
                                        row.get("market", String.class)))
                .all();
    }

    public Flux<MarketRankingItem> findAnalyzableStocks() {
        return database.sql(
                        """
                SELECT sm.code, sm.name, latest.close_price, latest.volume
                FROM stock_master sm
                JOIN daily_candle latest ON latest.code = sm.code
                    AND latest.trade_date = (
                        SELECT MAX(c.trade_date) FROM daily_candle c WHERE c.code = sm.code)
                WHERE sm.active = TRUE
                  AND (SELECT COUNT(*) FROM daily_candle c WHERE c.code = sm.code) >= 90
                ORDER BY sm.code
                """)
                .map(
                        row ->
                                new MarketRankingItem(
                                        row.get("code", String.class),
                                        row.get("name", String.class),
                                        number(row.get("close_price")),
                                        0,
                                        number(row.get("volume"))))
                .all();
    }

    public Flux<DailyPriceResponse> findDailyPrices(String code, int limit) {
        return database.sql(
                        """
                SELECT trade_date, open_price, high_price, low_price, close_price, volume
                FROM daily_candle
                WHERE code = :code
                ORDER BY trade_date DESC
                LIMIT :limit
                """)
                .bind("code", code)
                .bind("limit", limit)
                .map(
                        row ->
                                new DailyPriceResponse(
                                        row.get("trade_date", LocalDate.class)
                                                .format(
                                                        java.time.format.DateTimeFormatter
                                                                .BASIC_ISO_DATE),
                                        number(row.get("open_price")),
                                        number(row.get("high_price")),
                                        number(row.get("low_price")),
                                        number(row.get("close_price")),
                                        number(row.get("volume"))))
                .all();
    }

    private long number(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }
}
