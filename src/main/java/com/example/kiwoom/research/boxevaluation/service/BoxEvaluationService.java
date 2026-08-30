package com.example.kiwoom.research.boxevaluation.service;

import com.example.kiwoom.dto.StoredDailyCandle;
import com.example.kiwoom.research.boxevaluation.candidate.BoxCandidateGenerator;
import com.example.kiwoom.research.boxevaluation.dto.BoxEvaluationItemResponse;
import com.example.kiwoom.research.boxevaluation.dto.BoxEvaluationOutcome;
import com.example.kiwoom.research.boxevaluation.dto.BoxResearchDatasetRequest;
import com.example.kiwoom.research.boxevaluation.dto.CommitBoxEvaluationRequest;
import com.example.kiwoom.research.boxevaluation.dto.CreateBoxEvaluationBatchRequest;
import com.example.kiwoom.research.boxevaluation.dto.SaveBoxEvaluationDraftRequest;
import com.example.kiwoom.research.boxevaluation.dto.SaveFormationEvaluationRequest;
import com.example.kiwoom.research.boxevaluation.dto.SupersedeBoxEvaluationRequest;
import com.example.kiwoom.research.boxevaluation.model.BoxBoundaryDecision;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluation;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluationBatch;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluationBatchStatus;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluationCandidate;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluationDraft;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluationItem;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluationItemStatus;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluationProgress;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluationReveal;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluationSupersede;
import com.example.kiwoom.research.boxevaluation.model.BoxFormationEvaluation;
import com.example.kiwoom.research.boxevaluation.model.BoxResearchDataset;
import com.example.kiwoom.research.boxevaluation.model.FormationLabel;
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
    public static final String SCHEMA_VERSION = "box-label-v2";

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

    public Flux<BoxEvaluationItem> items(long batchId) {
        return repository.findItems(batchId);
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
                        request.boundaryDecision(),
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

    public Mono<BoxEvaluationProgress> progress(long batchId) {
        return repository.progress(batchId);
    }

    public Mono<BoxEvaluation> commit(long itemId, CommitBoxEvaluationRequest request) {
        return repository
                .findItem(itemId)
                .flatMap(
                        item -> {
                            validateBoundaryDecision(request);
                            Mono<Void> candidateValidation =
                                    request.boundaryDecision() == BoxBoundaryDecision.CANDIDATE
                                            ? repository
                                                    .findCandidates(itemId)
                                                    .filter(
                                                            candidate ->
                                                                    candidate
                                                                                    .candidateKey()
                                                                                    .equals(
                                                                                            request
                                                                                                    .selectedCandidateKey())
                                                                            && candidate
                                                                                    .startDate()
                                                                                    .equals(
                                                                                            request
                                                                                                    .startDate())
                                                                            && candidate
                                                                                    .endDate()
                                                                                    .equals(
                                                                                            request
                                                                                                    .endDate()))
                                                    .hasElements()
                                                    .flatMap(
                                                            valid ->
                                                                    valid
                                                                            ? Mono.empty()
                                                                            : Mono.error(
                                                                                    new IllegalArgumentException(
                                                                                            "선택 후보와 경계가 일치하지 않습니다.")))
                                            : Mono.empty();
                            return candidateValidation.then(
                                    repository
                                            .commit(
                                                    new BoxEvaluation(
                                                            null,
                                                            itemId,
                                                            request.reviewerId(),
                                                            request.commitKey(),
                                                            request.boundaryDecision(),
                                                            normalize(
                                                                    request.selectedCandidateKey()),
                                                            request.startDate(),
                                                            request.endDate(),
                                                            request.labelCode(),
                                                            request.confidence(),
                                                            request.reasonCodes(),
                                                            request.comment(),
                                                            json(item),
                                                            "box-label-v2",
                                                            Instant.now()))
                                            .flatMap(
                                                    saved ->
                                                            repository
                                                                    .closeBatchIfComplete(
                                                                            item.batchId())
                                                                    .thenReturn(saved)));
                        });
    }

    private void validateBoundaryDecision(CommitBoxEvaluationRequest request) {
        BoxBoundaryDecision decision = request.boundaryDecision();
        boolean hasStart = request.startDate() != null;
        boolean hasEnd = request.endDate() != null;
        boolean hasCandidate = normalize(request.selectedCandidateKey()) != null;
        boolean positiveLabel =
                "VALID_BOX".equals(request.labelCode())
                        || "PARTIAL_BOX".equals(request.labelCode());

        if (positiveLabel && decision == BoxBoundaryDecision.NO_SUITABLE_CANDIDATE) {
            throw new IllegalArgumentException("유효·부분 박스 평가는 경계를 지정해야 합니다.");
        }
        if (!positiveLabel && decision != BoxBoundaryDecision.NO_SUITABLE_CANDIDATE) {
            throw new IllegalArgumentException("비박스·자료 부족 평가는 적합 후보 없음으로 확정해야 합니다.");
        }
        if (decision == BoxBoundaryDecision.NO_SUITABLE_CANDIDATE
                && (hasCandidate || hasStart || hasEnd)) {
            throw new IllegalArgumentException("적합 후보 없음 평가에는 후보나 경계를 저장할 수 없습니다.");
        }
        if (decision == BoxBoundaryDecision.CANDIDATE && (!hasCandidate || !hasStart || !hasEnd)) {
            throw new IllegalArgumentException("후보 선택 평가는 후보와 시작·종료 경계가 필요합니다.");
        }
        if (decision == BoxBoundaryDecision.MANUAL && (hasCandidate || !hasStart || !hasEnd)) {
            throw new IllegalArgumentException("직접 경계 평가는 시작·종료 경계만 지정해야 합니다.");
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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

    public Mono<BoxEvaluation> evaluation(long itemId, String reviewerId) {
        return repository.findCommittedEvaluation(itemId, reviewerId);
    }

    public Mono<BoxFormationEvaluation> saveFormation(
            long itemId, SaveFormationEvaluationRequest request) {
        if (request.formationLabel() == FormationLabel.BOX
                && (request.finalStartDate() == null || request.finalEndDate() == null)) {
            return Mono.error(new IllegalArgumentException("BOX requires a final period"));
        }
        if (request.formationLabel() != FormationLabel.BOX
                && (request.finalStartDate() != null || request.finalEndDate() != null)) {
            return Mono.error(new IllegalArgumentException("non-BOX cannot contain a boundary"));
        }
        return repository
                .saveFormation(
                        new BoxFormationEvaluation(
                                null,
                                itemId,
                                request.reviewerId(),
                                request.formationLabel(),
                                null,
                                null,
                                request.finalStartDate(),
                                request.finalEndDate(),
                                request.periodDecision(),
                                request.proposedLowerSupportMin(),
                                request.proposedLowerSupportMax(),
                                request.proposedUpperResistanceMin(),
                                request.proposedUpperResistanceMax(),
                                request.finalLowerSupportMin(),
                                request.finalLowerSupportMax(),
                                request.finalUpperResistanceMin(),
                                request.finalUpperResistanceMax(),
                                request.zoneDecision(),
                                request.note(),
                                request.confidence(),
                                request.boundaryDecision(),
                                request.labelCode(),
                                request.reasonCodes(),
                                request.comment(),
                                request.expectedRevision() + 1,
                                Instant.now()),
                        request.expectedRevision())
                .flatMap(saved -> repository.markFormationComplete(itemId).thenReturn(saved));
    }

    public Mono<BoxFormationEvaluation> formation(long itemId, String reviewerId) {
        return repository.findFormation(itemId, reviewerId);
    }

    public Mono<BoxResearchDataset> createDataset(BoxResearchDatasetRequest request) {
        return repository.createDataset(
                new BoxResearchDataset(
                        null,
                        request.datasetKey(),
                        request.datasetType().name(),
                        request.sourceBatchId(),
                        request.samplingPolicyJson(),
                        request.blindPolicyVersion(),
                        request.featureSnapshotVersion(),
                        null));
    }

    public Flux<BoxResearchDataset> datasets() {
        return repository.findDatasets();
    }

    public Mono<BoxEvaluationBatch> createDiscoveryBatch(
            String datasetKey, CreateBoxEvaluationBatchRequest request) {
        return repository
                .findDatasets()
                .filter(
                        d ->
                                d.datasetKey().equals(datasetKey)
                                        && "DISCOVERY".equals(d.datasetType()))
                .next()
                .switchIfEmpty(
                        Mono.error(new IllegalArgumentException("DISCOVERY dataset not found")))
                .flatMap(ignored -> createBatch(request));
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
