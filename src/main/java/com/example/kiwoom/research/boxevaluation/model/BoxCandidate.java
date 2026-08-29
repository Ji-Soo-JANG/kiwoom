package com.example.kiwoom.research.boxevaluation.model;

import java.time.LocalDate;
import java.util.List;

public record BoxCandidate(
        BoxCandidateType type,
        LocalDate startDate,
        LocalDate endDate,
        BoxCandidateFeatures features,
        List<String> boundaryEvidence) {
    public BoxCandidate {
        boundaryEvidence = List.copyOf(boundaryEvidence);
    }
}
