package com.example.kiwoom.research.boxevaluation.service;

import com.example.kiwoom.dto.StoredDailyCandle;
import com.example.kiwoom.research.boxevaluation.candidate.BoxCandidateGenerator;
import com.example.kiwoom.research.boxevaluation.dto.BoxEvaluationItemResponse;
import com.example.kiwoom.research.boxevaluation.dto.BoxEvaluationOutcome;
import com.example.kiwoom.research.boxevaluation.dto.CommitBoxEvaluationRequest;
import com.example.kiwoom.research.boxevaluation.dto.CreateBoxEvaluationBatchRequest;
import com.example.kiwoom.research.boxevaluation.dto.SaveBoxEvaluationDraftRequest;
import com.example.kiwoom.research.boxevaluation.dto.SupersedeBoxEvaluationRequest;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluation;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluationBatch;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluationBatchStatus;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluationCandidate;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluationDraft;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluationItem;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluationItemStatus;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluationReveal;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluationSupersede;
import com.example.kiwoom.research.boxevaluation.repository.BoxEvaluationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class BoxEvaluationService {
    public static final String GENERATOR_VERSION = "box-candidate-research-v1";
    public static final String BLIND_VERSION = "server-cutoff-v1";
    public static final String SCHEMA_VERSION = "box-label-v1";

    private final BoxEvaluationRepository repository;
    private final ObjectMapper objectMapper;
    private final BoxCandidateGenerator generator = new BoxCandidateGenerator();
    private final BoxOutcomeCalculator outcomeCalculator = new BoxOutcomeCalculator();

    public BoxEvaluationService(BoxEvaluationRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public Flux<BoxEvaluationBatch> batches() {
        return repository.findBatches();
    }

    public Mono<BoxEvaluationBatch> createBatch(CreateBoxEvaluationBatchRequest request) {
        BoxEvaluationBatch batch =
                new BoxEvaluationBatch(
                        null,
                        request.strategyVersionId(),
                        request.name(),
                        request.datasetVersion(),
                        GENERATOR_VERSION,
                        json(request.items()),
                        BLIND_VERSION,
                        BoxEvaluationBatchStatus.READY,
                        request.createdBy(),
                        null);
        return repository
                .createBatch(batch)
                .flatMap(
                        saved ->
                                Flux.fromIterable(request.items())
                                        .index()
                                        .concatMap(
                                                indexed ->
                                                        createItem(
                                                                saved.id(),
                                                                indexed.getT1().intValue() + 1,
                                                                indexed.getT2()))
                                        .then(Mono.just(saved)));
    }

    private Mono<BoxEvaluationItem> createItem(
            long batchId, int order, CreateBoxEvaluationBatchRequest.Item request) {
        return repository
                .findBlindCandlesFor(request.code(), request.cutoffDate())
                .collectList()
                .flatMap(
                        candles -> {
                            if (candles.isEmpty())
                                return Mono.error(
                                        new IllegalArgumentException("cutoff 이하 일봉이 없습니다."));
                            BoxEvaluationItem item =
                                    new BoxEvaluationItem(
                                            null,
                                            batchId,
                                            request.code(),
                                            request.cutoffDate(),
                                            order,
                                            null,
                                            hash(candles),
                                            BoxEvaluationItemStatus.PENDING,
                                            0,
                                            null);
                            return repository
                                    .createItem(item)
                                    .flatMap(saved -> generateAndSave(saved, candles));
                        });
    }

    private Mono<BoxEvaluationItem> generateAndSave(
            BoxEvaluationItem item, List<StoredDailyCandle> candles) {
        var result = generator.generate(candles, item.cutoffDate());
        return Flux.fromIterable(result.candidates())
                .index()
                .concatMap(
                        indexed ->
                                repository.addCandidate(
                                        new BoxEvaluationCandidate(
                                                null,
                                                item.id(),
                                                indexed.getT2().type().name(),
                                                indexed.getT2().startDate(),
                                                indexed.getT2().endDate(),
                                                indexed.getT1().intValue() + 1,
                                                json(indexed.getT2().features()),
                                                GENERATOR_VERSION)))
                .then(Mono.just(item));
    }

    public Mono<BoxEvaluationItem> next(long batchId) {
        return repository.findNext(batchId);
    }

    public Mono<BoxEvaluationItemResponse> item(long itemId, String reviewerId) {
        return Mono.zip(
                        repository.findItem(itemId),
                        repository.findCandidates(itemId).collectList(),
                        repository
                                .findDraft(itemId, reviewerId)
                                .map(java.util.Optional::of)
                                .defaultIfEmpty(java.util.Optional.empty()))
                .map(
                        tuple ->
                                new BoxEvaluationItemResponse(
                                        tuple.getT1(),
                                        tuple.getT2(),
                                        tuple.getT3().orElse(null),
                                        SCHEMA_VERSION));
    }

    public Flux<StoredDailyCandle> candles(long itemId) {
        return repository.findBlindCandles(itemId);
    }

    public Mono<BoxEvaluationDraft> saveDraft(long itemId, SaveBoxEvaluationDraftRequest request) {
        return repository.saveDraft(
                new BoxEvaluationDraft(
                        null,
                        itemId,
                        request.reviewerId(),
                        request.selectedCandidateKey(),
                        request.startDate(),
                        request.endDate(),
                        request.labelCode(),
                        request.confidence(),
                        request.reasonCodes(),
                        request.comment(),
                        request.expectedRevision(),
                        null),
                request.expectedRevision());
    }

    public Mono<BoxEvaluation> commit(long itemId, CommitBoxEvaluationRequest request) {
        return repository
                .findItem(itemId)
                .flatMap(
                        item ->
                                repository.commit(
                                        new BoxEvaluation(
                                                null,
                                                itemId,
                                                request.reviewerId(),
                                                request.commitKey(),
                                                request.selectedCandidateKey(),
                                                request.startDate(),
                                                request.endDate(),
                                                request.labelCode(),
                                                request.confidence(),
                                                request.reasonCodes(),
                                                request.comment(),
                                                json(item),
                                                SCHEMA_VERSION,
                                                Instant.now())));
    }

    public Mono<BoxEvaluationSupersede> supersede(SupersedeBoxEvaluationRequest request) {
        return repository.supersede(
                new BoxEvaluationSupersede(
                        null,
                        request.evaluationId(),
                        request.replacementEvaluationId(),
                        request.reason(),
                        request.supersededBy(),
                        null));
    }

    public Mono<BoxEvaluationOutcome> reveal(long itemId, String requestedBy) {
        return repository
                .findCommittedEvaluationByItem(itemId)
                .flatMap(
                        evaluation ->
                                repository
                                        .findReveal(evaluation.id())
                                        .switchIfEmpty(
                                                Mono.zip(
                                                                repository.findItem(itemId),
                                                                repository
                                                                        .findOutcomeCandles(
                                                                                itemId, 20)
                                                                        .collectList())
                                                        .flatMap(
                                                                tuple -> {
                                                                    var outcome =
                                                                            outcomeCalculator
                                                                                    .calculate(
                                                                                            evaluation
                                                                                                    .id(),
                                                                                            tuple.getT1()
                                                                                                    .code(),
                                                                                            tuple.getT1()
                                                                                                    .cutoffDate(),
                                                                                            tuple
                                                                                                    .getT2());
                                                                    return repository.reveal(
                                                                            new BoxEvaluationReveal(
                                                                                    null,
                                                                                    evaluation.id(),
                                                                                    BoxOutcomeCalculator
                                                                                            .POLICY_VERSION,
                                                                                    requestedBy,
                                                                                    json(outcome),
                                                                                    null));
                                                                }))
                                        .map(saved -> readOutcome(saved.outcomeSnapshotJson())));
    }

    public Mono<BoxEvaluationOutcome> outcome(long itemId) {
        return repository
                .findCommittedEvaluationByItem(itemId)
                .flatMap(evaluation -> repository.findReveal(evaluation.id()))
                .map(saved -> readOutcome(saved.outcomeSnapshotJson()));
    }

    private BoxEvaluationOutcome readOutcome(String json) {
        try {
            return objectMapper.readValue(json, BoxEvaluationOutcome.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("성과 스냅샷을 읽을 수 없습니다.", exception);
        }
    }

    private String hash(List<StoredDailyCandle> candles) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (StoredDailyCandle candle : candles) {
                digest.update(candle.toString().getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("평가 스냅샷 직렬화 실패", exception);
        }
    }
}
