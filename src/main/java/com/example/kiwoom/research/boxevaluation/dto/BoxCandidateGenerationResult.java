package com.example.kiwoom.research.boxevaluation.dto;

import com.example.kiwoom.research.boxevaluation.model.BoxCandidate;
import java.time.LocalDate;
import java.util.List;

public record BoxCandidateGenerationResult(
        String code,
        LocalDate requestedCutoff,
        LocalDate dataAsOf,
        String status,
        List<BoxCandidate> candidates,
        List<String> messages) {
    public BoxCandidateGenerationResult {
        candidates = List.copyOf(candidates);
        messages = List.copyOf(messages);
    }
}
