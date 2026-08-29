package com.example.kiwoom.research.boxevaluation.model;

import java.time.Instant;

public record BoxEvaluationReveal(
        Long id,
        long evaluationId,
        String outcomePolicyVersion,
        String requestedBy,
        String outcomeSnapshotJson,
        Instant revealedAt) {}
