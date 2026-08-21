package com.example.kiwoom.dto;

import java.math.BigDecimal;

public record TradePerformanceSummary(
        long completedTrades,
        long winningTrades,
        BigDecimal winRate,
        BigDecimal averageGainRate,
        BigDecimal averageLossRate,
        BigDecimal payoffRatio,
        BigDecimal profitFactor,
        int consecutiveLosses,
        BigDecimal recentExpectancyRate,
        BigDecimal maximumDrawdownRate,
        BigDecimal totalNetPnl) {}
