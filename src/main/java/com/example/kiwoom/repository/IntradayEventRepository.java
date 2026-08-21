package com.example.kiwoom.repository;

import com.example.kiwoom.dto.IntradayPriceEvent;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class IntradayEventRepository {
    private final DatabaseClient database;

    public IntradayEventRepository(DatabaseClient database) {
        this.database = database;
    }

    public Mono<IntradayPriceEvent> save(IntradayPriceEvent event) {
        return findBySourceEventId(event.sourceEventId())
                .switchIfEmpty(
                        database.sql(
                                        """
                                INSERT INTO intraday_price_event(
                                    source_event_id, code, event_time, price, volume)
                                VALUES (:sourceId, :code, :eventTime, :price, :volume)
                                """)
                                .bind("sourceId", event.sourceEventId())
                                .bind("code", event.code())
                                .bind("eventTime", event.eventTime())
                                .bind("price", event.price())
                                .bind("volume", event.volume())
                                .filter(statement -> statement.returnGeneratedValues("id"))
                                .map(row -> ((Number) row.get("id")).longValue())
                                .one()
                                .flatMap(this::findById)
                                .onErrorResume(
                                        DuplicateKeyException.class,
                                        ignored -> findBySourceEventId(event.sourceEventId())));
    }

    public Flux<IntradayPriceEvent> replay(String code, Instant from, Instant to) {
        return database.sql(
                        """
                SELECT id, source_event_id, code, event_time, price, volume, received_at
                FROM intraday_price_event
                WHERE code = :code AND event_time >= :fromTime AND event_time <= :toTime
                ORDER BY event_time, id
                """)
                .bind("code", code)
                .bind("fromTime", from)
                .bind("toTime", to)
                .map(row -> map(row))
                .all();
    }

    private Mono<IntradayPriceEvent> findBySourceEventId(String sourceId) {
        return database.sql(
                        """
                SELECT id, source_event_id, code, event_time, price, volume, received_at
                FROM intraday_price_event WHERE source_event_id = :sourceId
                """)
                .bind("sourceId", sourceId)
                .map(row -> map(row))
                .one();
    }

    private Mono<IntradayPriceEvent> findById(long id) {
        return database.sql(
                        """
                SELECT id, source_event_id, code, event_time, price, volume, received_at
                FROM intraday_price_event WHERE id = :id
                """)
                .bind("id", id)
                .map(row -> map(row))
                .one();
    }

    private IntradayPriceEvent map(io.r2dbc.spi.Readable row) {
        return new IntradayPriceEvent(
                ((Number) row.get("id")).longValue(),
                row.get("source_event_id", String.class),
                row.get("code", String.class),
                instant(row.get("event_time")),
                ((Number) row.get("price")).longValue(),
                ((Number) row.get("volume")).longValue(),
                instant(row.get("received_at")));
    }

    private Instant instant(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime offset) return offset.toInstant();
        return Instant.parse(value.toString());
    }
}
