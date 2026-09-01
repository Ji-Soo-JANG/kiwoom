package com.example.kiwoom.research.boxevaluation.a1;

import java.time.LocalDate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public class A1BlindRepository {
    private final DatabaseClient database;

    public A1BlindRepository(DatabaseClient database) {
        this.database = database;
    }

    public Mono<A1BlindPayload> findPayload(long itemId) {
        return database.sql(
                        """
                        SELECT i.cutoff_date, c.trade_date, c.open_price, c.high_price,
                               c.low_price, c.close_price
                        FROM box_evaluation_item i
                        JOIN daily_candle c ON c.code=i.code
                        WHERE i.id=:item AND c.trade_date <= i.cutoff_date
                        ORDER BY c.trade_date
                        """)
                .bind("item", itemId)
                .map(
                        (row, metadata) ->
                                new CandleRow(
                                        row.get("cutoff_date", LocalDate.class),
                                        new A1BlindCandle(
                                                row.get("trade_date", LocalDate.class),
                                                ((Number) row.get("open_price")).longValue(),
                                                ((Number) row.get("high_price")).longValue(),
                                                ((Number) row.get("low_price")).longValue(),
                                                ((Number) row.get("close_price")).longValue())))
                .all()
                .collectList()
                .map(
                        rows ->
                                new A1BlindPayload(
                                        rows.isEmpty() ? null : rows.get(0).cutoffDate(),
                                        rows.stream().map(CandleRow::candle).toList()));
    }

    private record CandleRow(LocalDate cutoffDate, A1BlindCandle candle) {}
}
