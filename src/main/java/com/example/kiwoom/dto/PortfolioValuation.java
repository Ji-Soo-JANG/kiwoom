package com.example.kiwoom.dto;

import java.math.BigDecimal;

public record PortfolioValuation(
        String code,
        BigDecimal quantity,
        BigDecimal averagePrice,
        BigDecimal currentPrice,
        BigDecimal purchaseAmount,
        BigDecimal evaluationAmount,
        BigDecimal profitLoss,
        BigDecimal returnRate) {}
