package com.example.kiwoom.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;

public record TradeCandidateRequest(
        @NotBlank String signalId,
        @NotBlank @Pattern(regexp = "\\d{6}") String code,
        @NotBlank String reason,
        @NotNull @Positive BigDecimal referencePrice,
        @Positive long suggestedQuantity,
        @NotNull @Future Instant expiresAt) {}
