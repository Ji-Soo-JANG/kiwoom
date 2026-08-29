package com.example.kiwoom.research.boxevaluation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record SupersedeBoxEvaluationRequest(
        @Positive long evaluationId,
        @Positive long replacementEvaluationId,
        @NotBlank String reason,
        @NotBlank String supersededBy) {}
