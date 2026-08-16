package com.example.kiwoom.repository;

import com.example.kiwoom.dto.PortfolioTrade;
import com.example.kiwoom.dto.TradeType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class PortfolioTradeRepository {
    private final DatabaseClient database;

    public PortfolioTradeRepository(DatabaseClient database) {
        this.database = database;
    }

    public Mono<PortfolioTrade> save(String username, PortfolioTrade trade) {
        return database.sql(
                        """
                INSERT INTO portfolio_trade(username, code, trade_type, quantity, price, fee, tax, realized_profit_loss, traded_at)
                VALUES (:username, :code, :type, :quantity, :price, :fee, :tax, :realized, :tradedAt)
                """)
                .bind("username", username)
                .bind("code", trade.code())
                .bind("type", trade.type().name())
                .bind("quantity", trade.quantity())
                .bind("price", trade.price())
                .bind("fee", trade.fee())
                .bind("tax", trade.tax())
                .bind("realized", trade.realizedProfitLoss())
                .bind("tradedAt", trade.tradedAt())
                .filter(statement -> statement.returnGeneratedValues("id"))
                .map(
                        row ->
                                new PortfolioTrade(
                                        row.get("id", Long.class),
                                        trade.code(),
                                        trade.type(),
                                        trade.quantity(),
                                        trade.price(),
                                        trade.fee(),
                                        trade.tax(),
                                        trade.realizedProfitLoss(),
                                        trade.tradedAt()))
                .one();
    }

    public Flux<PortfolioTrade> findAll(String username) {
        return database.sql(
                        """
                SELECT id, code, trade_type, quantity, price, fee, tax, realized_profit_loss, traded_at
                FROM portfolio_trade WHERE username = :username ORDER BY traded_at DESC, id DESC
                """)
                .bind("username", username)
                .map(
                        (row, metadata) ->
                                new PortfolioTrade(
                                        row.get("id", Long.class),
                                        row.get("code", String.class),
                                        TradeType.valueOf(row.get("trade_type", String.class)),
                                        row.get("quantity", BigDecimal.class),
                                        row.get("price", BigDecimal.class),
                                        row.get("fee", BigDecimal.class),
                                        row.get("tax", BigDecimal.class),
                                        row.get("realized_profit_loss", BigDecimal.class),
                                        row.get("traded_at", OffsetDateTime.class)))
                .all();
    }
}
