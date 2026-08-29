package com.example.kiwoom.research.boxevaluation.model;

import java.time.Instant;
import java.time.LocalDate;

public record BoxEvaluation(
        Long id,
        long itemId,
        String reviewerId,
        String commitKey,
        BoxBoundaryDecision boundaryDecision,
        String selectedCandidateKey,
        LocalDate finalStartDate,
        LocalDate finalEndDate,
        String labelCode,
        int confidence,
        String reasonCodes,
        String comment,
        String inputSnapshotJson,
        String evaluationSchemaVersion,
        Instant committedAt) {}
