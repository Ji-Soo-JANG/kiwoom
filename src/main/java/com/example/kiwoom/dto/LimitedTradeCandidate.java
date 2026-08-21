package com.example.kiwoom.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record LimitedTradeCandidate(
        long id,
        String signalId,
        String code,
        String reason,
        BigDecimal referencePrice,
        long suggestedQuantity,
        String status,
        Instant expiresAt,
        String approvedBy,
        Instant approvedAt,
        Long orderId,
        Instant createdAt,
        Instant updatedAt) {}
