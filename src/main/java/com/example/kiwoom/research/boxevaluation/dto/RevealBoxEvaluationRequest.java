package com.example.kiwoom.research.boxevaluation.dto;

import jakarta.validation.constraints.NotBlank;

public record RevealBoxEvaluationRequest(@NotBlank String requestedBy) {}
