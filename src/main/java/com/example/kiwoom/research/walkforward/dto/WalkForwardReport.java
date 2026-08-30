package com.example.kiwoom.research.walkforward.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record WalkForwardReport(
        Long reportId,
        String strategyVersion,
        String code,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        int trainingDays,
        int validationDays,
        int stepDays,
        int foldCount,
        int validationTradeCount,
        BigDecimal costAdjustedExpectancy,
        double maxDrawdownRate,
        double averageReturnRate,
        BigDecimal costDrag,
        boolean passed,
        String verdict,
        List<WalkForwardFold> folds,
        Instant createdAt) {
    public WalkForwardReport withReportId(long id) {
        return new WalkForwardReport(
                id,
                strategyVersion,
                code,
                name,
                startDate,
                endDate,
                trainingDays,
                validationDays,
                stepDays,
                foldCount,
                validationTradeCount,
                costAdjustedExpectancy,
                maxDrawdownRate,
                averageReturnRate,
                costDrag,
                passed,
                verdict,
                folds,
                createdAt);
    }
}
