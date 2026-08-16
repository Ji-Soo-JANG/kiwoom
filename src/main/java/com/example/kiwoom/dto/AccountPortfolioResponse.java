package com.example.kiwoom.dto;

import java.time.Instant;
import java.util.List;

public record AccountPortfolioResponse(
        String accountNumber,
        long totalPurchaseAmount,
        long totalEvaluationAmount,
        long totalProfitLoss,
        double totalReturnRate,
        long estimatedAssets,
        List<AccountPosition> positions,
        Instant updatedAt) {}
