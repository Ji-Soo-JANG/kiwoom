package com.example.kiwoom.dto;

import java.util.List;

public record TradingModeStatus(
        TradingMode requestedMode,
        TradingMode effectiveMode,
        boolean liveArmed,
        boolean externalOrderSubmissionAvailable,
        List<String> blockers) {}
