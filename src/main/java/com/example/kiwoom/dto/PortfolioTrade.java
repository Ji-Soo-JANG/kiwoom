package com.example.kiwoom.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PortfolioTrade(
        Long id,
        String code,
        TradeType type,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal fee,
        BigDecimal tax,
        BigDecimal realizedProfitLoss,
        OffsetDateTime tradedAt
) {
}
