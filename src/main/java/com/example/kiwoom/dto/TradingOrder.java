package com.example.kiwoom.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TradingOrder(
        long id,
        String decisionId,
        TradingMode mode,
        String code,
        OrderSide side,
        long requestedQuantity,
        BigDecimal requestedPrice,
        OrderStatus status,
        long filledQuantity,
        BigDecimal averageFillPrice,
        String rejectionReason,
        Instant createdAt,
        Instant updatedAt) {}
