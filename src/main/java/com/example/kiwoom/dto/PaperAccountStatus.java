package com.example.kiwoom.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record PaperAccountStatus(
        BigDecimal initialCash,
        BigDecimal cash,
        BigDecimal peakEquity,
        LocalDate tradingDay,
        BigDecimal dayStartEquity,
        boolean killSwitchActive,
        String killSwitchReason,
        Instant killSwitchActivatedAt) {}
