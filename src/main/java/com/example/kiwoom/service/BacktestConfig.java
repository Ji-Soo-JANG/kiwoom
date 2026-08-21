package com.example.kiwoom.service;

import com.example.kiwoom.dto.BacktestRequest;
import java.math.BigDecimal;

record BacktestConfig(
        BigDecimal initialCapital,
        double positionSizeRate,
        double feeRate,
        double taxRate,
        double slippageRate,
        double stopLossRate,
        double takeProfitRate,
        int maxHoldingDays,
        int boxRangeDays) {
    static BacktestConfig from(BacktestRequest request) {
        return new BacktestConfig(
                BigDecimal.valueOf(value(request.initialCapital(), 10_000_000)),
                value(request.positionSizeRate(), 0.2),
                value(request.feeRate(), 0.00015),
                value(request.taxRate(), 0.0018),
                value(request.slippageRate(), 0.001),
                value(request.stopLossRate(), 0.08),
                value(request.takeProfitRate(), 0.15),
                request.maxHoldingDays() == null ? 20 : request.maxHoldingDays(),
                request.boxRangeDays() == null ? 60 : request.boxRangeDays());
    }

    private static double value(Double value, double fallback) {
        return value == null ? fallback : value;
    }

    BacktestConfig withoutCosts() {
        return new BacktestConfig(
                initialCapital,
                positionSizeRate,
                0,
                0,
                0,
                stopLossRate,
                takeProfitRate,
                maxHoldingDays,
                boxRangeDays);
    }
}
