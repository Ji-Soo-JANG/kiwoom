package com.example.kiwoom.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PortfolioProfitPoint(
        LocalDate date,
        BigDecimal realizedProfitLoss,
        BigDecimal unrealizedProfitLoss,
        BigDecimal totalProfitLoss) {}
