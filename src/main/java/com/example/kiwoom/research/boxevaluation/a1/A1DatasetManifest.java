package com.example.kiwoom.research.boxevaluation.a1;

import java.util.List;

public record A1DatasetManifest(
        String datasetKey,
        String datasetType,
        String stage,
        long seed,
        String algorithmVersion,
        List<A1Sample> samples) {}
