package com.example.kiwoom.research.boxevaluation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BoxResearchDatasetRequest(
        @NotBlank String datasetKey,
        @NotNull DatasetType datasetType,
        Long sourceBatchId,
        @NotBlank String samplingPolicyJson,
        @NotBlank String blindPolicyVersion,
        @NotBlank String featureSnapshotVersion) {
    public enum DatasetType {
        DISCOVERY,
        BOUNDARY,
        HOLDOUT,
        REGRESSION
    }
}
