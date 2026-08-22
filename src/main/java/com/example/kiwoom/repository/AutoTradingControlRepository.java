package com.example.kiwoom.repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public class AutoTradingControlRepository {
    private final DatabaseClient database;

    public AutoTradingControlRepository(DatabaseClient database) {
        this.database = database;
    }

    public Mono<StoredControl> get() {
        return database.sql("SELECT * FROM auto_trading_control WHERE id=1")
                .map(
                        (row, meta) ->
                                new StoredControl(
                                        Boolean.TRUE.equals(
                                                row.get("paper_enabled", Boolean.class)),
                                        row.get("paper_strategy", String.class),
                                        Boolean.TRUE.equals(row.get("live_enabled", Boolean.class)),
                                        row.get("live_strategy", String.class),
                                        row.get("updated_by", String.class),
                                        instant(row.get("updated_at"))))
                .one();
    }

    public Mono<StoredControl> update(
            boolean paper, String paperStrategy, boolean live, String liveStrategy, String user) {
        return database.sql(
                        """
                UPDATE auto_trading_control SET paper_enabled=:paper, paper_strategy=:paperStrategy,
                    live_enabled=:live, live_strategy=:liveStrategy, updated_by=:user, updated_at=CURRENT_TIMESTAMP
                WHERE id=1
                """)
                .bind("paper", paper)
                .bind("paperStrategy", paperStrategy)
                .bind("live", live)
                .bind("liveStrategy", liveStrategy)
                .bind("user", user)
                .fetch()
                .rowsUpdated()
                .then(get());
    }

    private Instant instant(Object value) {
        return value instanceof Instant i
                ? i
                : value instanceof OffsetDateTime o
                        ? o.toInstant()
                        : Instant.parse(value.toString());
    }

    public record StoredControl(
            boolean paperEnabled,
            String paperStrategy,
            boolean liveEnabled,
            String liveStrategy,
            String updatedBy,
            Instant updatedAt) {}
}
