package com.example.kiwoom.research.boxevaluation.dto;

import java.time.LocalDate;
import java.util.List;

public record BoxEvaluationOutcome(
        String policyVersion,
        long evaluationId,
        String code,
        LocalDate cutoffDate,
        LocalDate entryDate,
        long entryPrice,
        List<Window> windows,
        String firstBarrier,
        LocalDate firstBarrierDate) {
    public BoxEvaluationOutcome {
        windows = List.copyOf(windows);
    }

    public record Window(
            int tradingDays,
            LocalDate endDate,
            double closeReturnRate,
            double maximumFavorableExcursion,
            double maximumAdverseExcursion) {}
}
