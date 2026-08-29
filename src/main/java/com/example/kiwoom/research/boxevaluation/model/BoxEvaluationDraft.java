package com.example.kiwoom.research.boxevaluation.model;

import java.time.Instant;
import java.time.LocalDate;

public record BoxEvaluationDraft(
        Long id,
        long itemId,
        String reviewerId,
        BoxBoundaryDecision boundaryDecision,
        String selectedCandidateKey,
        LocalDate editedStartDate,
        LocalDate editedEndDate,
        String labelCode,
        Integer confidence,
        String reasonCodes,
        String comment,
        long draftRevision,
        Instant updatedAt) {}
