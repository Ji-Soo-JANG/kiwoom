package com.example.kiwoom.dto;

import java.math.BigDecimal;

public record PortfolioTradeRequest(
        String code,
        TradeType type,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal fee,
        BigDecimal tax) {}
