package com.example.kiwoom.research.boxevaluation.dto;

import com.example.kiwoom.research.boxevaluation.model.FormationLabel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record SaveFormationEvaluationRequest(
        @NotBlank String reviewerId,
        @NotNull FormationLabel formationLabel,
        LocalDate finalStartDate,
        LocalDate finalEndDate,
        String periodDecision,
        BigDecimal proposedLowerSupportMin,
        BigDecimal proposedLowerSupportMax,
        BigDecimal proposedUpperResistanceMin,
        BigDecimal proposedUpperResistanceMax,
        BigDecimal finalLowerSupportMin,
        BigDecimal finalLowerSupportMax,
        BigDecimal finalUpperResistanceMin,
        BigDecimal finalUpperResistanceMax,
        String zoneDecision,
        String note,
        Integer confidence,
        String boundaryDecision,
        String labelCode,
        String reasonCodes,
        String comment,
        long expectedRevision) {}
