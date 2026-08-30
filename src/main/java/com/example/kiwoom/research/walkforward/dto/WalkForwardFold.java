package com.example.kiwoom.research.walkforward.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WalkForwardFold(
        int foldNo,
        LocalDate trainingStart,
        LocalDate trainingEnd,
        LocalDate validationStart,
        LocalDate validationEnd,
        int trainingTradeCount,
        double trainingReturnRate,
        int validationTradeCount,
        double validationWinRate,
        BigDecimal validationExpectancy,
        double validationReturnRate,
        double validationMaxDrawdownRate,
        BigDecimal costDrag) {}
