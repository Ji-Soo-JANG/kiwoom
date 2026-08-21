package com.example.kiwoom.dto;

public record ObservationReport(
        long id,
        String name,
        String strategyVersion,
        int minimumTradingDays,
        long observedTradingDays,
        long sampleCount,
        long matchingSignals,
        long missedSignals,
        long unexpectedSignals,
        double agreementRate,
        double averagePriceDeviationRate,
        boolean complete) {}
