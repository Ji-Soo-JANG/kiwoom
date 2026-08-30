package com.example.kiwoom.research.backtest.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record BacktestResponse(
        Long runId,
        String strategyVersion,
        String code,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal initialCapital,
        BigDecimal finalCapital,
        double feeRate,
        double taxRate,
        double slippageRate,
        int tradeCount,
        double winRate,
        double totalReturnRate,
        double maxDrawdownRate,
        BigDecimal expectancy,
        List<BacktestTrade> trades,
        Instant createdAt) {
    public BacktestResponse withRunId(long id) {
        return new BacktestResponse(
                id,
                strategyVersion,
                code,
                name,
                startDate,
                endDate,
                initialCapital,
                finalCapital,
                feeRate,
                taxRate,
                slippageRate,
                tradeCount,
                winRate,
                totalReturnRate,
                maxDrawdownRate,
                expectancy,
                trades,
                createdAt);
    }
}
