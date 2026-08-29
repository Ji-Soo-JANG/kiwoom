package com.example.kiwoom.research.boxevaluation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record CommitBoxEvaluationRequest(
        @NotBlank String reviewerId,
        @NotBlank String commitKey,
        String selectedCandidateKey,
        LocalDate startDate,
        LocalDate endDate,
        @NotBlank String labelCode,
        @Min(1) @Max(5) int confidence,
        @NotBlank String reasonCodes,
        String comment) {}
