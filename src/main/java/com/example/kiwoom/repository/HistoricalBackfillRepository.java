package com.example.kiwoom.repository;

import com.example.kiwoom.dto.DailyPriceResponse;
import com.example.kiwoom.service.HistoricalBackfillState;
import com.example.kiwoom.service.HistoricalBackfillStatus;
import com.example.kiwoom.service.HistoricalExhaustionReason;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.r2dbc.BadSqlGrammarException;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class HistoricalBackfillRepository {
    private static final DateTimeFormatter BASIC = DateTimeFormatter.BASIC_ISO_DATE;
    private static final int CANDLE_BATCH_SIZE = 100;
    private final DatabaseClient database;

    public HistoricalBackfillRepository(DatabaseClient database) {
        this.database = database;
    }

    public Mono<HistoricalBackfillState> find(String code) {
        return database.sql(
                        """
                SELECT code,target_start_date,oldest_synced_date,status,exhaustion_reason,
                       continuation_key,continuation_active,page_count,candle_count,attempt_count,
                       last_error_code,last_error_message
                FROM historical_backfill_state WHERE code=:code
                """)
                .bind("code", code)
                .map(
                        (row, metadata) ->
                                new HistoricalBackfillState(
                                        row.get("code", String.class),
                                        row.get("target_start_date", LocalDate.class),
                                        row.get("oldest_synced_date", LocalDate.class),
                                        HistoricalBackfillStatus.valueOf(
                                                row.get("status", String.class)),
                                        enumValue(
                                                row.get("exhaustion_reason", String.class),
                                                HistoricalExhaustionReason.class),
                                        row.get("continuation_key", String.class),
                                        Boolean.TRUE.equals(
                                                row.get("continuation_active", Boolean.class)),
                                        intNumber(row.get("page_count")),
                                        longNumber(row.get("candle_count")),
                                        intNumber(row.get("attempt_count")),
                                        row.get("last_error_code", String.class),
                                        row.get("last_error_message", String.class)))
                .one();
    }

    public Mono<Void> start(String code, LocalDate targetStartDate, LocalDate oldestDate) {
        DatabaseClient.GenericExecuteSpec sql =
                database.sql(
                                """
                INSERT INTO historical_backfill_state(code,target_start_date,oldest_synced_date,status,
                    continuation_active,page_count,candle_count,attempt_count,started_at,updated_at)
                VALUES (:code,:target,:oldest,'IN_PROGRESS',FALSE,0,0,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                ON CONFLICT (code) DO UPDATE SET target_start_date=EXCLUDED.target_start_date,
                    oldest_synced_date=EXCLUDED.oldest_synced_date,status='IN_PROGRESS',
                    exhaustion_reason=NULL,continuation_key=NULL,continuation_active=FALSE,
                    page_count=0,candle_count=0,attempt_count=historical_backfill_state.attempt_count+1,
                    last_error_code=NULL,last_error_message=NULL,started_at=CURRENT_TIMESTAMP,
                    completed_at=NULL,updated_at=CURRENT_TIMESTAMP
                """)
                        .bind("code", code)
                        .bind("target", targetStartDate);
        sql =
                oldestDate == null
                        ? sql.bindNull("oldest", LocalDate.class)
                        : sql.bind("oldest", oldestDate);
        return sql.fetch()
                .rowsUpdated()
                .then()
                .onErrorResume(
                        BadSqlGrammarException.class,
                        error -> startH2(code, targetStartDate, oldestDate));
    }

    /** Creates the durable queue state before a worker claims it. */
    public Mono<Void> createPending(String code, LocalDate targetStartDate) {
        return database.sql(
                        """
                INSERT INTO historical_backfill_state(code,target_start_date,status,
                    continuation_active,page_count,candle_count,attempt_count,started_at,updated_at)
                VALUES (:code,:target,'PENDING',FALSE,0,0,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                ON CONFLICT (code) DO NOTHING
                """)
                .bind("code", code)
                .bind("target", targetStartDate)
                .fetch()
                .rowsUpdated()
                .then()
                .onErrorResume(
                        BadSqlGrammarException.class,
                        error ->
                                database.sql(
                                                """
                                        INSERT INTO historical_backfill_state(code,target_start_date,status,
                                            continuation_active,page_count,candle_count,attempt_count,started_at,updated_at)
                                        SELECT :code,:target,'PENDING',FALSE,0,0,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
                                        WHERE NOT EXISTS (SELECT 1 FROM historical_backfill_state WHERE code=:code)
                                        """)
                                        .bind("code", code)
                                        .bind("target", targetStartDate)
                                        .fetch()
                                        .rowsUpdated()
                                        .then());
    }

    private Mono<Void> startH2(String code, LocalDate targetStartDate, LocalDate oldestDate) {
        DatabaseClient.GenericExecuteSpec update =
                database.sql(
                                """
                        UPDATE historical_backfill_state SET target_start_date=:target,
                            oldest_synced_date=:oldest,status='IN_PROGRESS',exhaustion_reason=NULL,
                            continuation_key=NULL,continuation_active=FALSE,page_count=0,candle_count=0,
                            attempt_count=attempt_count+1,last_error_code=NULL,last_error_message=NULL,
                            started_at=CURRENT_TIMESTAMP,completed_at=NULL,updated_at=CURRENT_TIMESTAMP
                        WHERE code=:code
                        """)
                        .bind("code", code)
                        .bind("target", targetStartDate);
        update =
                oldestDate == null
                        ? update.bindNull("oldest", LocalDate.class)
                        : update.bind("oldest", oldestDate);
        return update.fetch()
                .rowsUpdated()
                .flatMap(
                        rows -> {
                            if (rows > 0) return Mono.empty();
                            DatabaseClient.GenericExecuteSpec insert =
                                    database.sql(
                                                    """
                                            INSERT INTO historical_backfill_state(code,target_start_date,oldest_synced_date,status,
                                                continuation_active,page_count,candle_count,attempt_count,started_at,updated_at)
                                            VALUES (:code,:target,:oldest,'IN_PROGRESS',FALSE,0,0,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                                            """)
                                            .bind("code", code)
                                            .bind("target", targetStartDate);
                            insert =
                                    oldestDate == null
                                            ? insert.bindNull("oldest", LocalDate.class)
                                            : insert.bind("oldest", oldestDate);
                            return insert.fetch().rowsUpdated().then();
                        });
    }

    public Mono<Void> checkpoint(
            String code,
            LocalDate oldestDate,
            String continuationKey,
            boolean continuationActive,
            int pageCount,
            long candleCount) {
        if (continuationKey != null && continuationKey.length() > 500) {
            return Mono.error(
                    new IllegalArgumentException("continuation key exceeds 500 characters"));
        }
        DatabaseClient.GenericExecuteSpec sql =
                database.sql(
                                """
                UPDATE historical_backfill_state SET oldest_synced_date=:oldest,
                    continuation_key=:nextKey,continuation_active=:active,page_count=:pages,
                    candle_count=:candles,last_checkpoint_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP
                WHERE code=:code
                """)
                        .bind("code", code)
                        .bind("active", continuationActive)
                        .bind("pages", pageCount)
                        .bind("candles", candleCount);
        sql =
                continuationKey == null
                        ? sql.bindNull("nextKey", String.class)
                        : sql.bind("nextKey", continuationKey);
        sql =
                oldestDate == null
                        ? sql.bindNull("oldest", LocalDate.class)
                        : sql.bind("oldest", oldestDate);
        return sql.fetch().rowsUpdated().then();
    }

    public Mono<Void> finish(
            String code, HistoricalBackfillStatus status, HistoricalExhaustionReason reason) {
        DatabaseClient.GenericExecuteSpec sql =
                database.sql(
                                """
                UPDATE historical_backfill_state SET status=:status,exhaustion_reason=:reason,
                    continuation_active=FALSE,completed_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP
                WHERE code=:code
                """)
                        .bind("code", code)
                        .bind("status", status.name());
        sql =
                reason == null
                        ? sql.bindNull("reason", String.class)
                        : sql.bind("reason", reason.name());
        return sql.fetch().rowsUpdated().then();
    }

    public Mono<Void> fail(String code, String errorCode, String message) {
        return database.sql(
                        """
                UPDATE historical_backfill_state SET status='FAILED',last_error_code=:codeError,
                    last_error_message=:message,updated_at=CURRENT_TIMESTAMP WHERE code=:code
                """)
                .bind("code", code)
                .bind("codeError", errorCode == null ? "UNKNOWN" : errorCode)
                .bind(
                        "message",
                        message == null
                                ? "unknown"
                                : message.substring(0, Math.min(500, message.length())))
                .fetch()
                .rowsUpdated()
                .then();
    }

    /** Atomically upserts one broker page and advances its durable checkpoint. */
    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<Void> persistPage(
            String code,
            java.util.List<DailyPriceResponse> candles,
            LocalDate oldestDate,
            String continuationKey,
            boolean continuationActive,
            int pageCount,
            long candleCount) {
        return Flux.fromIterable(chunks(candles, CANDLE_BATCH_SIZE))
                .concatMap(chunk -> upsertCandleBatch(code, chunk))
                .then(
                        checkpoint(
                                code,
                                oldestDate,
                                continuationKey,
                                continuationActive,
                                pageCount,
                                candleCount));
    }

    private Mono<Void> upsertCandleBatch(String code, List<DailyPriceResponse> candles) {
        StringBuilder values = new StringBuilder();
        for (int index = 0; index < candles.size(); index++) {
            DailyPriceResponse candle = candles.get(index);
            if (index > 0) values.append(',');
            values.append("(:code")
                    .append(index)
                    .append(",:tradeDate")
                    .append(index)
                    .append(",:open")
                    .append(index)
                    .append(",:high")
                    .append(index)
                    .append(",:low")
                    .append(index)
                    .append(",:close")
                    .append(index)
                    .append(",:volume")
                    .append(index)
                    .append(')');
        }
        DatabaseClient.GenericExecuteSpec sql =
                database.sql(
                        "INSERT INTO daily_candle(code,trade_date,open_price,high_price,low_price,close_price,volume) VALUES "
                                + values
                                + " ON CONFLICT (code,trade_date) DO UPDATE SET "
                                + "open_price=EXCLUDED.open_price, high_price=EXCLUDED.high_price, "
                                + "low_price=EXCLUDED.low_price, close_price=EXCLUDED.close_price, "
                                + "volume=EXCLUDED.volume, updated_at=CURRENT_TIMESTAMP");
        for (int index = 0; index < candles.size(); index++) {
            DailyPriceResponse candle = candles.get(index);
            sql =
                    sql.bind("code" + index, code)
                            .bind("tradeDate" + index, LocalDate.parse(candle.getDate(), BASIC))
                            .bind("open" + index, candle.getOpenPrice())
                            .bind("high" + index, candle.getHighPrice())
                            .bind("low" + index, candle.getLowPrice())
                            .bind("close" + index, candle.getClosePrice())
                            .bind("volume" + index, candle.getVolume());
        }
        return sql.fetch()
                .rowsUpdated()
                .then()
                .onErrorResume(
                        BadSqlGrammarException.class,
                        error ->
                                Flux.fromIterable(candles)
                                        .concatMap(candle -> mergeCandleH2(code, candle))
                                        .then());
    }

    private Mono<Void> mergeCandleH2(String code, DailyPriceResponse candle) {
        return database.sql(
                        """
                MERGE INTO daily_candle (code, trade_date, open_price, high_price, low_price, close_price, volume)
                KEY (code, trade_date)
                VALUES (:code,:tradeDate,:open,:high,:low,:close,:volume)
                """)
                .bind("code", code)
                .bind("tradeDate", LocalDate.parse(candle.getDate(), BASIC))
                .bind("open", candle.getOpenPrice())
                .bind("high", candle.getHighPrice())
                .bind("low", candle.getLowPrice())
                .bind("close", candle.getClosePrice())
                .bind("volume", candle.getVolume())
                .fetch()
                .rowsUpdated()
                .then();
    }

    private static List<List<DailyPriceResponse>> chunks(
            List<DailyPriceResponse> candles, int size) {
        List<List<DailyPriceResponse>> result = new ArrayList<>();
        for (int from = 0; from < candles.size(); from += size) {
            int to = Math.min(from + size, candles.size());
            // Preserve the old serial-upsert semantics if a broker page repeats a natural key.
            LinkedHashMap<String, DailyPriceResponse> unique = new LinkedHashMap<>();
            for (DailyPriceResponse candle : candles.subList(from, to)) {
                unique.put(candle.getDate(), candle);
            }
            result.add(new ArrayList<>(unique.values()));
        }
        return result;
    }

    private static <E extends Enum<E>> E enumValue(String value, Class<E> type) {
        return value == null ? null : Enum.valueOf(type, value);
    }

    private static int intNumber(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }

    private static long longNumber(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }
}
