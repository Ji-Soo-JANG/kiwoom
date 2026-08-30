package com.example.kiwoom.research.boxevaluation.controller;

import com.example.kiwoom.dto.StoredDailyCandle;
import com.example.kiwoom.research.boxevaluation.dto.BoxEvaluationItemResponse;
import com.example.kiwoom.research.boxevaluation.dto.BoxEvaluationOutcome;
import com.example.kiwoom.research.boxevaluation.dto.BoxResearchDatasetRequest;
import com.example.kiwoom.research.boxevaluation.dto.CommitBoxEvaluationRequest;
import com.example.kiwoom.research.boxevaluation.dto.CreateBoxEvaluationBatchRequest;
import com.example.kiwoom.research.boxevaluation.dto.RevealBoxEvaluationRequest;
import com.example.kiwoom.research.boxevaluation.dto.SaveBoxEvaluationDraftRequest;
import com.example.kiwoom.research.boxevaluation.dto.SaveFormationEvaluationRequest;
import com.example.kiwoom.research.boxevaluation.dto.SupersedeBoxEvaluationRequest;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluation;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluationBatch;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluationDraft;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluationItem;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluationSupersede;
import com.example.kiwoom.research.boxevaluation.service.BoxEvaluationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/research/box-evaluations")
public class BoxEvaluationController {
    private final BoxEvaluationService service;

    public BoxEvaluationController(BoxEvaluationService service) {
        this.service = service;
    }

    @GetMapping("/batches")
    public Flux<BoxEvaluationBatch> batches() {
        return service.batches();
    }

    @PostMapping("/batches")
    public Mono<BoxEvaluationBatch> createBatch(
            @Valid @RequestBody CreateBoxEvaluationBatchRequest request) {
        return service.createBatch(request);
    }

    @GetMapping("/batches/{batchId}/next")
    public Mono<BoxEvaluationItem> next(@PathVariable long batchId) {
        return service.next(batchId);
    }

    @GetMapping("/batches/{batchId}/items")
    public Flux<BoxEvaluationItem> items(@PathVariable long batchId) {
        return service.items(batchId);
    }

    @GetMapping("/batches/{batchId}/progress")
    public Mono<com.example.kiwoom.research.boxevaluation.model.BoxEvaluationProgress> progress(
            @PathVariable long batchId) {
        return service.progress(batchId);
    }

    @GetMapping("/items/{itemId}")
    public Mono<BoxEvaluationItemResponse> item(
            @PathVariable long itemId, @RequestParam String reviewerId) {
        return service.item(itemId, reviewerId);
    }

    @GetMapping("/items/{itemId}/candles")
    public Flux<StoredDailyCandle> candles(@PathVariable long itemId) {
        return service.candles(itemId);
    }

    @PutMapping("/items/{itemId}/draft")
    public Mono<BoxEvaluationDraft> saveDraft(
            @PathVariable long itemId, @Valid @RequestBody SaveBoxEvaluationDraftRequest request) {
        return service.saveDraft(itemId, request);
    }

    @PostMapping("/items/{itemId}/commit")
    public Mono<BoxEvaluation> commit(
            @PathVariable long itemId, @Valid @RequestBody CommitBoxEvaluationRequest request) {
        return service.commit(itemId, request);
    }

    @PostMapping("/items/{itemId}/supersede")
    public Mono<BoxEvaluationSupersede> supersede(
            @PathVariable long itemId, @Valid @RequestBody SupersedeBoxEvaluationRequest request) {
        return service.supersede(request);
    }

    @PostMapping("/items/{itemId}/reveal")
    public Mono<BoxEvaluationOutcome> reveal(
            @PathVariable long itemId, @Valid @RequestBody RevealBoxEvaluationRequest request) {
        return service.reveal(itemId, request.requestedBy());
    }

    @GetMapping("/items/{itemId}/outcome")
    public Mono<BoxEvaluationOutcome> outcome(@PathVariable long itemId) {
        return service.outcome(itemId);
    }

    @GetMapping("/items/{itemId}/evaluation")
    public Mono<BoxEvaluation> evaluation(
            @PathVariable long itemId, @RequestParam String reviewerId) {
        return service.evaluation(itemId, reviewerId);
    }

    @GetMapping("/items/{itemId}/formation")
    public Mono<com.example.kiwoom.research.boxevaluation.model.BoxFormationEvaluation> formation(
            @PathVariable long itemId, @RequestParam String reviewerId) {
        return service.formation(itemId, reviewerId);
    }

    @PutMapping("/items/{itemId}/formation")
    public Mono<com.example.kiwoom.research.boxevaluation.model.BoxFormationEvaluation>
            saveFormation(
                    @PathVariable long itemId,
                    @Valid @RequestBody SaveFormationEvaluationRequest request) {
        return service.saveFormation(itemId, request);
    }

    @GetMapping("/datasets")
    public Flux<com.example.kiwoom.research.boxevaluation.model.BoxResearchDataset> datasets() {
        return service.datasets();
    }

    @PostMapping("/datasets/{datasetKey}/batches")
    public Mono<BoxEvaluationBatch> createDiscoveryBatch(
            @PathVariable String datasetKey,
            @Valid @RequestBody CreateBoxEvaluationBatchRequest request) {
        return service.createDiscoveryBatch(datasetKey, request);
    }

    @PostMapping("/datasets")
    public Mono<com.example.kiwoom.research.boxevaluation.model.BoxResearchDataset> createDataset(
            @Valid @RequestBody BoxResearchDatasetRequest request) {
        return service.createDataset(request);
    }
}
