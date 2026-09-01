package com.example.kiwoom.service;

import java.time.LocalDate;

public record HistoricalBackfillState(
        String code,
        LocalDate targetStartDate,
        LocalDate oldestSyncedDate,
        HistoricalBackfillStatus status,
        HistoricalExhaustionReason exhaustionReason,
        String continuationKey,
        boolean continuationActive,
        int pageCount,
        long candleCount,
        int attemptCount,
        String lastErrorCode,
        String lastErrorMessage) {}
