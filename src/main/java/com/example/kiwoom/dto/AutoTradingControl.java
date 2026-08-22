package com.example.kiwoom.dto;

import java.time.Instant;
import java.util.List;

public record AutoTradingControl(
        boolean paperEnabled,
        String paperStrategy,
        boolean liveEnabled,
        String liveStrategy,
        boolean liveExecutionAvailable,
        List<String> availableStrategies,
        List<String> liveBlockers,
        String updatedBy,
        Instant updatedAt) {}
