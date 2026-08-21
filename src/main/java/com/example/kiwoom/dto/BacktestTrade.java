package com.example.kiwoom.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BacktestTrade(
        LocalDate entryDate,
        LocalDate exitDate,
        BigDecimal entryPrice,
        BigDecimal exitPrice,
        long quantity,
        BigDecimal grossProfitLoss,
        BigDecimal fee,
        BigDecimal tax,
        BigDecimal slippageCost,
        BigDecimal netProfitLoss,
        double returnRate,
        String exitReason) {}
