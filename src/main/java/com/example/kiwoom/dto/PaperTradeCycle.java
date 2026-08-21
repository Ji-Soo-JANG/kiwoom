package com.example.kiwoom.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PaperTradeCycle(
        long id,
        long entryCandidateId,
        String code,
        long quantity,
        long entryOrderId,
        BigDecimal entryPrice,
        BigDecimal stopLossPrice,
        BigDecimal takeProfitPrice,
        int maxHoldingDays,
        String status,
        String exitReason,
        BigDecimal exitTriggerPrice,
        Long exitOrderId,
        Instant openedAt,
        Instant exitRequestedAt,
        Instant closedAt) {}
