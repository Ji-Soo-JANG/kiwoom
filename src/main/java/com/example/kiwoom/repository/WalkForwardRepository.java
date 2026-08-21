package com.example.kiwoom.repository;

import com.example.kiwoom.dto.WalkForwardFold;
import com.example.kiwoom.dto.WalkForwardReport;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class WalkForwardRepository {
    private final DatabaseClient database;

    public WalkForwardRepository(DatabaseClient database) {
        this.database = database;
    }

    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<WalkForwardReport> save(WalkForwardReport report) {
        return database.sql(
                        """
                INSERT INTO walk_forward_report(
                    strategy_version, code, start_date, end_date, training_days,
                    validation_days, step_days, fold_count, validation_trade_count,
                    cost_adjusted_expectancy, max_drawdown_rate, average_return_rate,
                    cost_drag, passed)
                VALUES (:version, :code, :startDate, :endDate, :trainingDays,
                    :validationDays, :stepDays, :foldCount, :tradeCount,
                    :expectancy, :maxDrawdown, :averageReturn, :costDrag, :passed)
                """)
                .bind("version", report.strategyVersion())
                .bind("code", report.code())
                .bind("startDate", report.startDate())
                .bind("endDate", report.endDate())
                .bind("trainingDays", report.trainingDays())
                .bind("validationDays", report.validationDays())
                .bind("stepDays", report.stepDays())
                .bind("foldCount", report.foldCount())
                .bind("tradeCount", report.validationTradeCount())
                .bind("expectancy", report.costAdjustedExpectancy())
                .bind("maxDrawdown", report.maxDrawdownRate())
                .bind("averageReturn", report.averageReturnRate())
                .bind("costDrag", report.costDrag())
                .bind("passed", report.passed())
                .filter(statement -> statement.returnGeneratedValues("id"))
                .map(row -> ((Number) row.get("id")).longValue())
                .one()
                .flatMap(
                        reportId ->
                                Flux.fromIterable(report.folds())
                                        .concatMap(fold -> saveFold(reportId, fold))
                                        .then(Mono.just(report.withReportId(reportId))));
    }

    private Mono<Void> saveFold(long reportId, WalkForwardFold fold) {
        return database.sql(
                        """
                INSERT INTO walk_forward_fold(
                    report_id, fold_no, training_start, training_end,
                    validation_start, validation_end, training_trade_count,
                    training_return_rate, validation_trade_count, validation_win_rate,
                    validation_expectancy, validation_return_rate,
                    validation_max_drawdown_rate, cost_drag)
                VALUES (:reportId, :foldNo, :trainingStart, :trainingEnd,
                    :validationStart, :validationEnd, :trainingTrades,
                    :trainingReturn, :validationTrades, :validationWinRate,
                    :validationExpectancy, :validationReturn,
                    :validationDrawdown, :costDrag)
                """)
                .bind("reportId", reportId)
                .bind("foldNo", fold.foldNo())
                .bind("trainingStart", fold.trainingStart())
                .bind("trainingEnd", fold.trainingEnd())
                .bind("validationStart", fold.validationStart())
                .bind("validationEnd", fold.validationEnd())
                .bind("trainingTrades", fold.trainingTradeCount())
                .bind("trainingReturn", fold.trainingReturnRate())
                .bind("validationTrades", fold.validationTradeCount())
                .bind("validationWinRate", fold.validationWinRate())
                .bind("validationExpectancy", fold.validationExpectancy())
                .bind("validationReturn", fold.validationReturnRate())
                .bind("validationDrawdown", fold.validationMaxDrawdownRate())
                .bind("costDrag", fold.costDrag())
                .fetch()
                .rowsUpdated()
                .then();
    }
}
