package com.example.kiwoom.dto;

import java.time.Instant;
import java.util.List;

public record MarketDataQualityReport(
        long runId,
        String policyVersion,
        int stockCount,
        long candleCount,
        int blockingIssueCount,
        int warningIssueCount,
        List<MarketDataQualityIssue> issueSample,
        Instant checkedAt) {}
