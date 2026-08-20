package com.example.kiwoom.dto;

import java.time.Instant;
import java.time.LocalDate;

public record MarketDataSyncStatus(
        long stockCount,
        long candleCount,
        long syncedStockCount,
        long failedStockCount,
        LocalDate latestTradeDate,
        int processedInLastRun,
        int succeededInLastRun,
        int failedInLastRun,
        boolean running,
        Instant checkedAt) {}
