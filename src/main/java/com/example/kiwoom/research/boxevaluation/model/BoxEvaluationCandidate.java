package com.example.kiwoom.research.boxevaluation.model;

import java.time.LocalDate;

public record BoxEvaluationCandidate(
        Long id,
        long itemId,
        String candidateKey,
        LocalDate startDate,
        LocalDate endDate,
        int rankNo,
        String featureJson,
        String generatorVersion) {}
