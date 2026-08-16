package com.example.kiwoom.dto;

import java.util.List;

public record StrategyCandidate(
        String code,
        String name,
        long currentPrice,
        int score,
        boolean qualified,
        double drawdownRate,
        double boxRangeRate,
        int volumeSpikeCount,
        double breakoutRate,
        double pullbackRate,
        List<String> matchedConditions) {}
