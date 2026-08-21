package com.example.kiwoom.dto;

public record AccountPosition(
        String code,
        String name,
        long quantity,
        long availableQuantity,
        long averagePrice,
        long currentPrice,
        long purchaseAmount,
        long evaluationAmount,
        long profitLoss,
        double returnRate,
        double weight,
        double profitContribution) {}
