package com.example.kiwoom.research.boxevaluation.model;

public record BoxCandidateFeatures(
        int tradingDays,
        long medianClose,
        long lowerClose,
        long upperClose,
        double robustRangeRate,
        double closeSlopePerDay,
        int volumeSpikeCount,
        double maximumVolumeMultiple) {}
