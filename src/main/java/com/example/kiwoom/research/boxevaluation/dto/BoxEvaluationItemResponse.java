package com.example.kiwoom.research.boxevaluation.dto;

import com.example.kiwoom.research.boxevaluation.model.BoxEvaluationCandidate;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluationDraft;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluationItem;
import java.util.List;

/** Deliberately contains no outcome or future-availability fields. */
public record BoxEvaluationItemResponse(
        BoxEvaluationItem item,
        List<BoxEvaluationCandidate> candidates,
        BoxEvaluationDraft draft,
        String evaluationSchemaVersion) {}
