package com.example.kiwoom.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TradingPerformanceStatus(
        long sampleCount,
        BigDecimal averageSlippageRate,
        BigDecimal averageNetReturnRate,
        BigDecimal maximumSlippageRate,
        boolean halted,
        String haltReason,
        Instant evaluatedAt) {}
