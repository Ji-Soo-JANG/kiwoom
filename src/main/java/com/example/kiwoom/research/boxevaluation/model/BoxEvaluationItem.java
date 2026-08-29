package com.example.kiwoom.research.boxevaluation.model;

import java.time.Instant;
import java.time.LocalDate;

public record BoxEvaluationItem(
        Long id,
        long batchId,
        String code,
        LocalDate cutoffDate,
        int displayOrder,
        Long sourceScanId,
        String dataHash,
        BoxEvaluationItemStatus status,
        long lockVersion,
        Instant createdAt) {}
