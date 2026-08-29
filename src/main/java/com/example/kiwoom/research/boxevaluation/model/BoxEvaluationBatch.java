package com.example.kiwoom.research.boxevaluation.model;

import java.time.Instant;

public record BoxEvaluationBatch(
        Long id,
        long strategyVersionId,
        String name,
        String datasetVersion,
        String candidateGeneratorVersion,
        String samplingPolicyJson,
        String blindPolicyVersion,
        BoxEvaluationBatchStatus status,
        String createdBy,
        Instant createdAt) {}
