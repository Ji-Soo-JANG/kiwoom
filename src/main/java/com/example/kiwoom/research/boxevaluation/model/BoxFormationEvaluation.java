package com.example.kiwoom.research.boxevaluation.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record BoxFormationEvaluation(
        Long id,
        long itemId,
        String reviewerId,
        FormationLabel formationLabel,
        LocalDate proposedStartDate,
        LocalDate proposedEndDate,
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
        long revision,
        Instant committedAt) {}
