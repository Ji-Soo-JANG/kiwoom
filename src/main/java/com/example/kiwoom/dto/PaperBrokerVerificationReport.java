package com.example.kiwoom.dto;

import java.time.Instant;
import java.util.List;

public record PaperBrokerVerificationReport(
        Instant verifiedAt,
        boolean partialFill,
        boolean unfilled,
        boolean amendment,
        boolean cancellation,
        boolean recovery,
        boolean duplicateExecutionIgnored,
        List<String> trace) {
    public boolean passed() {
        return partialFill
                && unfilled
                && amendment
                && cancellation
                && recovery
                && duplicateExecutionIgnored;
    }
}
