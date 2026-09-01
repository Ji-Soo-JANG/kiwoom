package com.example.kiwoom.research.boxevaluation.a1;

import com.example.kiwoom.research.boxevaluation.model.BoxEvaluationBatch;
import com.example.kiwoom.research.boxevaluation.model.BoxResearchDataset;
import java.util.List;

public record A1GenerationResult(
        BoxResearchDataset dataset, BoxEvaluationBatch batch, List<A1Sample> samples) {
    public A1GenerationResult {
        samples = List.copyOf(samples);
    }
}
