package com.example.kiwoom.research.boxevaluation.a1;

public record A1EligibilityEvidence(
        String historicalBackfillStatus,
        int minimumContextCandles,
        int actualContextCandleCount,
        boolean cutoffIsActualTradingDate) {}
