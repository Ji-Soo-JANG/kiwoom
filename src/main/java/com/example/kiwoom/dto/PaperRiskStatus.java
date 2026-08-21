package com.example.kiwoom.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PaperRiskStatus(
        BigDecimal equity,
        BigDecimal cash,
        BigDecimal grossExposure,
        int openPositionCount,
        double dailyReturnRate,
        double drawdownRate,
        BigDecimal maxPositionRate,
        BigDecimal maxGrossExposureRate,
        BigDecimal maxDailyLossRate,
        BigDecimal maxDrawdownRate,
        int maxOpenPositions,
        boolean killSwitchActive,
        String killSwitchReason,
        Instant checkedAt) {}
