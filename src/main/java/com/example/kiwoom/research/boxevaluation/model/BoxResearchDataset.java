package com.example.kiwoom.research.boxevaluation.model;

import java.time.Instant;

public record BoxResearchDataset(
        Long id,
        String datasetKey,
        String datasetType,
        Long sourceBatchId,
        String samplingPolicyJson,
        String blindPolicyVersion,
        String featureSnapshotVersion,
        Instant createdAt) {}
