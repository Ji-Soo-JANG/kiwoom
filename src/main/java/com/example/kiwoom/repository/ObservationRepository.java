package com.example.kiwoom.repository;

import com.example.kiwoom.dto.ObservationReport;
import com.example.kiwoom.dto.ObservationRequest;
import com.example.kiwoom.dto.ObservationSampleRequest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public class ObservationRepository {
    private final DatabaseClient database;

    public ObservationRepository(DatabaseClient database) {
        this.database = database;
    }

    public Mono<Long> create(ObservationRequest request) {
        return database.sql(
                        "INSERT INTO signal_observation(name, strategy_version) VALUES (:name, :version)")
                .bind("name", request.name())
                .bind("version", request.strategyVersion())
                .filter(statement -> statement.returnGeneratedValues("id"))
                .map(row -> ((Number) row.get("id")).longValue())
                .one();
    }

    public Mono<Void> addSample(long id, ObservationSampleRequest sample) {
        DatabaseClient.GenericExecuteSpec update =
                database.sql(
                                """
                        UPDATE signal_observation_sample SET backtest_signal=:backtest,
                            realtime_signal=:realtime, expected_price=:expected, observed_price=:observed
                        WHERE observation_id=:id AND trading_day=:day AND code=:code
                        """)
                        .bind("id", id)
                        .bind("day", sample.tradingDay())
                        .bind("code", sample.code())
                        .bind("backtest", sample.backtestSignal())
                        .bind("realtime", sample.realtimeSignal());
        update = bindNullable(update, "expected", sample.expectedPrice(), Long.class);
        update = bindNullable(update, "observed", sample.observedPrice(), Long.class);
        return update.fetch()
                .rowsUpdated()
                .flatMap(rows -> rows > 0 ? Mono.empty() : insert(id, sample));
    }

    private Mono<Void> insert(long id, ObservationSampleRequest sample) {
        DatabaseClient.GenericExecuteSpec query =
                database.sql(
                                """
                INSERT INTO signal_observation_sample(observation_id, trading_day, code,
                    backtest_signal, realtime_signal, expected_price, observed_price)
                VALUES (:id, :day, :code, :backtest, :realtime, :expected, :observed)
                """)
                        .bind("id", id)
                        .bind("day", sample.tradingDay())
                        .bind("code", sample.code())
                        .bind("backtest", sample.backtestSignal())
                        .bind("realtime", sample.realtimeSignal());
        query = bindNullable(query, "expected", sample.expectedPrice(), Long.class);
        query = bindNullable(query, "observed", sample.observedPrice(), Long.class);
        return query.then();
    }

    public Mono<ObservationReport> report(long id) {
        return database.sql(
                        """
                SELECT o.id, o.name, o.strategy_version, o.minimum_trading_days,
                       COUNT(DISTINCT s.trading_day) observed_days, COUNT(s.id) sample_count,
                       COALESCE(SUM(CASE WHEN s.backtest_signal = s.realtime_signal THEN 1 ELSE 0 END), 0) matching,
                       COALESCE(SUM(CASE WHEN s.backtest_signal AND NOT s.realtime_signal THEN 1 ELSE 0 END), 0) missed,
                       COALESCE(SUM(CASE WHEN NOT s.backtest_signal AND s.realtime_signal THEN 1 ELSE 0 END), 0) unexpected,
                       COALESCE(AVG(CASE WHEN s.expected_price > 0 AND s.observed_price IS NOT NULL
                           THEN ABS(s.observed_price - s.expected_price) * 100.0 / s.expected_price END), 0) deviation
                FROM signal_observation o LEFT JOIN signal_observation_sample s ON s.observation_id = o.id
                WHERE o.id = :id GROUP BY o.id, o.name, o.strategy_version, o.minimum_trading_days
                """)
                .bind("id", id)
                .map(
                        row -> {
                            long count = number(row.get("sample_count")).longValue();
                            long matching = number(row.get("matching")).longValue();
                            long days = number(row.get("observed_days")).longValue();
                            int minimum = number(row.get("minimum_trading_days")).intValue();
                            return new ObservationReport(
                                    number(row.get("id")).longValue(),
                                    row.get("name", String.class),
                                    row.get("strategy_version", String.class),
                                    minimum,
                                    days,
                                    count,
                                    matching,
                                    number(row.get("missed")).longValue(),
                                    number(row.get("unexpected")).longValue(),
                                    count == 0 ? 0 : matching * 100.0 / count,
                                    number(row.get("deviation")).doubleValue(),
                                    days >= minimum);
                        })
                .one();
    }

    public Mono<ObservationReport> latest(String strategyVersion) {
        return database.sql(
                        "SELECT id FROM signal_observation WHERE strategy_version=:version ORDER BY id DESC LIMIT 1")
                .bind("version", strategyVersion)
                .map(row -> ((Number) row.get("id")).longValue())
                .one()
                .flatMap(this::report);
    }

    private DatabaseClient.GenericExecuteSpec bindNullable(
            DatabaseClient.GenericExecuteSpec query, String name, Object value, Class<?> type) {
        return value == null ? query.bindNull(name, type) : query.bind(name, value);
    }

    private Number number(Object value) {
        return value == null ? 0 : (Number) value;
    }
}
