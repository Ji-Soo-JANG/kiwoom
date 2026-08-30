package com.example.kiwoom.research.backtest.repository;

import com.example.kiwoom.research.backtest.dto.BacktestResponse;
import com.example.kiwoom.research.backtest.dto.BacktestTrade;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class BacktestRepository {
    private final DatabaseClient database;

    public BacktestRepository(DatabaseClient database) {
        this.database = database;
    }

    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<BacktestResponse> save(BacktestResponse result) {
        return database.sql(
                        """
                INSERT INTO backtest_run(
                    strategy_version, code, start_date, end_date, initial_capital,
                    final_capital, fee_rate, tax_rate, slippage_rate, trade_count,
                    win_rate, total_return_rate, max_drawdown_rate, expectancy)
                VALUES (:version, :code, :startDate, :endDate, :initialCapital,
                    :finalCapital, :feeRate, :taxRate, :slippageRate, :tradeCount,
                    :winRate, :totalReturnRate, :maxDrawdownRate, :expectancy)
                """)
                .bind("version", result.strategyVersion())
                .bind("code", result.code())
                .bind("startDate", result.startDate())
                .bind("endDate", result.endDate())
                .bind("initialCapital", result.initialCapital())
                .bind("finalCapital", result.finalCapital())
                .bind("feeRate", result.feeRate())
                .bind("taxRate", result.taxRate())
                .bind("slippageRate", result.slippageRate())
                .bind("tradeCount", result.tradeCount())
                .bind("winRate", result.winRate())
                .bind("totalReturnRate", result.totalReturnRate())
                .bind("maxDrawdownRate", result.maxDrawdownRate())
                .bind("expectancy", result.expectancy())
                .filter(statement -> statement.returnGeneratedValues("id"))
                .map(row -> ((Number) row.get("id")).longValue())
                .one()
                .flatMap(
                        runId ->
                                Flux.fromIterable(result.trades())
                                        .concatMap(trade -> saveTrade(runId, trade))
                                        .then(Mono.just(result.withRunId(runId))));
    }

    private Mono<Void> saveTrade(long runId, BacktestTrade trade) {
        return database.sql(
                        """
                INSERT INTO backtest_trade(
                    run_id, entry_date, exit_date, entry_price, exit_price, quantity,
                    gross_profit_loss, fee, tax, slippage_cost, net_profit_loss,
                    return_rate, exit_reason)
                VALUES (:runId, :entryDate, :exitDate, :entryPrice, :exitPrice, :quantity,
                    :gross, :fee, :tax, :slippage, :net, :returnRate, :reason)
                """)
                .bind("runId", runId)
                .bind("entryDate", trade.entryDate())
                .bind("exitDate", trade.exitDate())
                .bind("entryPrice", trade.entryPrice())
                .bind("exitPrice", trade.exitPrice())
                .bind("quantity", trade.quantity())
                .bind("gross", trade.grossProfitLoss())
                .bind("fee", trade.fee())
                .bind("tax", trade.tax())
                .bind("slippage", trade.slippageCost())
                .bind("net", trade.netProfitLoss())
                .bind("returnRate", trade.returnRate())
                .bind("reason", trade.exitReason())
                .fetch()
                .rowsUpdated()
                .then();
    }
}
