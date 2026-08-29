package com.example.kiwoom.research.boxevaluation.model;

import java.time.Instant;

public record BoxEvaluationSupersede(
        Long id,
        long evaluationId,
        long supersededByEvaluationId,
        String reason,
        String supersededBy,
        Instant supersededAt) {}
