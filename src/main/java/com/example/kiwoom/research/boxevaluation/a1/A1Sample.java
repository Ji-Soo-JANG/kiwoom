package com.example.kiwoom.research.boxevaluation.a1;

import java.time.LocalDate;

public record A1Sample(
        String code,
        String market,
        LocalDate cutoffDate,
        A1TimeStratum assignedStratum,
        A1TimeStratum actualStratum,
        boolean fallbackApplied,
        String historicalBackfillStatus,
        int minimumContextCandles,
        int actualContextCandleCount,
        boolean cutoffIsActualTradingDate) {
    public A1Sample(
            String code,
            String market,
            LocalDate cutoffDate,
            A1TimeStratum assignedStratum,
            A1TimeStratum actualStratum,
            boolean fallbackApplied) {
        this(
                code,
                market,
                cutoffDate,
                assignedStratum,
                actualStratum,
                fallbackApplied,
                null,
                0,
                0,
                false);
    }

    public A1Sample withEligibilityEvidence(A1EligibilityEvidence evidence) {
        return new A1Sample(
                code,
                market,
                cutoffDate,
                assignedStratum,
                actualStratum,
                fallbackApplied,
                evidence.historicalBackfillStatus(),
                evidence.minimumContextCandles(),
                evidence.actualContextCandleCount(),
                evidence.cutoffIsActualTradingDate());
    }
}
