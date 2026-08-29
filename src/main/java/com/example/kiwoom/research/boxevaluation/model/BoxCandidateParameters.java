package com.example.kiwoom.research.boxevaluation.model;

public record BoxCandidateParameters(
        int minimumSampleDays,
        int anchorLookbackDays,
        double recoveryAnchorRate,
        double narrowDailyDeviationRate,
        double expandedDailyDeviationRate,
        int maximumConnectionGapDays,
        double connectedMedianDifferenceRate,
        double volumeSpikeMultiple) {

    public BoxCandidateParameters {
        if (minimumSampleDays < 10)
            throw new IllegalArgumentException("minimumSampleDays must be >= 10");
        if (anchorLookbackDays < 1)
            throw new IllegalArgumentException("anchorLookbackDays must be positive");
        if (maximumConnectionGapDays < 0)
            throw new IllegalArgumentException("maximumConnectionGapDays must not be negative");
        requireRate(recoveryAnchorRate, "recoveryAnchorRate");
        requireRate(narrowDailyDeviationRate, "narrowDailyDeviationRate");
        requireRate(expandedDailyDeviationRate, "expandedDailyDeviationRate");
        requireRate(connectedMedianDifferenceRate, "connectedMedianDifferenceRate");
        if (narrowDailyDeviationRate > expandedDailyDeviationRate)
            throw new IllegalArgumentException(
                    "narrow deviation must not exceed expanded deviation");
        if (volumeSpikeMultiple <= 1)
            throw new IllegalArgumentException("volumeSpikeMultiple must be greater than one");
    }

    public static BoxCandidateParameters defaults() {
        return new BoxCandidateParameters(20, 30, 0.08, 0.10, 0.20, 5, 0.08, 2.5);
    }

    private static void requireRate(double value, String name) {
        if (value <= 0 || value >= 1)
            throw new IllegalArgumentException(name + " must be between 0 and 1");
    }
}
