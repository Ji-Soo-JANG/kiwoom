package com.example.kiwoom.research.boxevaluation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;

public record CreateBoxEvaluationBatchRequest(
        @Positive long strategyVersionId,
        @NotBlank String name,
        @NotBlank String datasetVersion,
        @NotBlank String createdBy,
        @NotEmpty List<@Valid Item> items) {
    public record Item(@NotBlank String code, LocalDate cutoffDate) {}
}
