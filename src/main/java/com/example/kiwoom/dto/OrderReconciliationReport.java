package com.example.kiwoom.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderReconciliationReport(
        String scope,
        long orderCount,
        long fillCount,
        BigDecimal storedCash,
        BigDecimal expectedCash,
        List<String> mismatches,
        boolean consistent,
        Instant checkedAt) {}
