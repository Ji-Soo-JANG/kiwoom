package com.example.kiwoom.dto;

import java.time.Instant;
import java.util.List;

public record StrategyScanResponse(
        List<StrategyCandidate> candidates, int scannedCount, String scope, Instant updatedAt) {}
