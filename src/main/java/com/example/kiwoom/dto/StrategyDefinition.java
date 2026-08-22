package com.example.kiwoom.dto;

import java.time.Instant;
import java.util.Map;

public record StrategyDefinition(
        long id,
        String strategyId,
        int version,
        String versionKey,
        String name,
        String description,
        String status,
        Map<String, Object> parameters,
        Instant createdAt) {}
