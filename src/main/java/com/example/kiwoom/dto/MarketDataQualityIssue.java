package com.example.kiwoom.dto;

import java.time.LocalDate;

public record MarketDataQualityIssue(
        String code, LocalDate tradeDate, String issueType, String severity, String detail) {
    public boolean blocking() {
        return "BLOCKING".equals(severity);
    }
}
