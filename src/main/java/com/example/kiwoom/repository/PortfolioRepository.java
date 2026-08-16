package com.example.kiwoom.repository;

import com.example.kiwoom.dto.PortfolioPosition;
import java.math.BigDecimal;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class PortfolioRepository {
    private final DatabaseClient database;

    public PortfolioRepository(DatabaseClient database) {
        this.database = database;
    }

    public Flux<PortfolioPosition> findAll(String username) {
        return database.sql(
                        "SELECT code, quantity, average_price FROM portfolio_position WHERE username = :username ORDER BY code")
                .bind("username", username)
                .map(
                        (row, metadata) ->
                                new PortfolioPosition(
                                        row.get("code", String.class),
                                        row.get("quantity", BigDecimal.class),
                                        row.get("average_price", BigDecimal.class)))
                .all();
    }

    public Mono<PortfolioPosition> findByCode(String username, String code) {
        return database.sql(
                        "SELECT code, quantity, average_price FROM portfolio_position WHERE username = :username AND code = :code")
                .bind("username", username)
                .bind("code", code)
                .map(
                        (row, metadata) ->
                                new PortfolioPosition(
                                        row.get("code", String.class),
                                        row.get("quantity", BigDecimal.class),
                                        row.get("average_price", BigDecimal.class)))
                .one();
    }

    public Mono<PortfolioPosition> save(String username, PortfolioPosition position) {
        return database.sql(
                        """
                UPDATE portfolio_position SET quantity = :quantity, average_price = :averagePrice,
                  updated_at = CURRENT_TIMESTAMP WHERE username = :username AND code = :code
                """)
                .bind("username", username)
                .bind("code", position.code())
                .bind("quantity", position.quantity())
                .bind("averagePrice", position.averagePrice())
                .fetch()
                .rowsUpdated()
                .flatMap(updated -> updated > 0 ? Mono.just(position) : insert(username, position));
    }

    private Mono<PortfolioPosition> insert(String username, PortfolioPosition position) {
        return database.sql(
                        """
                INSERT INTO portfolio_position(username, code, quantity, average_price)
                VALUES (:username, :code, :quantity, :averagePrice)
                """)
                .bind("username", username)
                .bind("code", position.code())
                .bind("quantity", position.quantity())
                .bind("averagePrice", position.averagePrice())
                .fetch()
                .rowsUpdated()
                .thenReturn(position);
    }

    public Mono<Void> remove(String username, String code) {
        return database.sql(
                        "DELETE FROM portfolio_position WHERE username = :username AND code = :code")
                .bind("username", username)
                .bind("code", code)
                .fetch()
                .rowsUpdated()
                .then();
    }
}
