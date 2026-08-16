package com.example.kiwoom.repository;

import com.example.kiwoom.dto.PortfolioPosition;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@Repository
public class PortfolioRepository {
    private final DatabaseClient database;

    public PortfolioRepository(DatabaseClient database) { this.database = database; }

    public Flux<PortfolioPosition> findAll() {
        return database.sql("SELECT code, quantity, average_price FROM portfolio_position ORDER BY code")
                .map((row, metadata) -> new PortfolioPosition(row.get("code", String.class),
                        row.get("quantity", BigDecimal.class), row.get("average_price", BigDecimal.class)))
                .all();
    }

    public Mono<PortfolioPosition> save(PortfolioPosition position) {
        return database.sql("""
                UPDATE portfolio_position SET quantity = :quantity, average_price = :averagePrice,
                  updated_at = CURRENT_TIMESTAMP WHERE code = :code
                """)
                .bind("code", position.code()).bind("quantity", position.quantity())
                .bind("averagePrice", position.averagePrice()).fetch().rowsUpdated()
                .flatMap(updated -> updated > 0 ? Mono.just(position) : insert(position));
    }

    private Mono<PortfolioPosition> insert(PortfolioPosition position) {
        return database.sql("""
                INSERT INTO portfolio_position(code, quantity, average_price)
                VALUES (:code, :quantity, :averagePrice)
                """).bind("code", position.code()).bind("quantity", position.quantity())
                .bind("averagePrice", position.averagePrice()).fetch().rowsUpdated().thenReturn(position);
    }

    public Mono<Void> remove(String code) {
        return database.sql("DELETE FROM portfolio_position WHERE code = :code")
                .bind("code", code).fetch().rowsUpdated().then();
    }
}
