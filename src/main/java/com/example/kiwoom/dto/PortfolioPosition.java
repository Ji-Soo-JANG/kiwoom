package com.example.kiwoom.dto;

import java.math.BigDecimal;

public record PortfolioPosition(
        String code,
        BigDecimal quantity,
        BigDecimal averagePrice
) {
}
