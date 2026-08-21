package com.example.kiwoom.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record StrategyScanResponse(
        long scanId,
        String strategyVersion,
        int boxRangeDays,
        List<StrategyCandidate> candidates,
        int scannedCount,
        String scope,
        LocalDate dataAsOf,
        Instant updatedAt) {}
