package com.example.kiwoom.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PaperTradeResult(
        long cycleId,
        BigDecimal grossPnl,
        BigDecimal totalCost,
        BigDecimal netPnl,
        BigDecimal netReturnRate,
        int holdingDays,
        String exitReason,
        Instant closedAt) {}
