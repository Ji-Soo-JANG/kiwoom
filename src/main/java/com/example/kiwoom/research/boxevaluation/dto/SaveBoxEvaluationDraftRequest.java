package com.example.kiwoom.research.boxevaluation.dto;

import com.example.kiwoom.research.boxevaluation.model.BoxBoundaryDecision;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record SaveBoxEvaluationDraftRequest(
        @NotBlank String reviewerId,
        BoxBoundaryDecision boundaryDecision,
        String selectedCandidateKey,
        LocalDate startDate,
        LocalDate endDate,
        String labelCode,
        @Min(1) @Max(5) Integer confidence,
        String reasonCodes,
        String comment,
        @Min(0) long expectedRevision) {}
