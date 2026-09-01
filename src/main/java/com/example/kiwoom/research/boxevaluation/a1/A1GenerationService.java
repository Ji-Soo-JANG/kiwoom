package com.example.kiwoom.research.boxevaluation.a1;

import com.example.kiwoom.research.boxevaluation.dto.CreateBoxEvaluationBatchRequest;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluationBatch;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluationItem;
import com.example.kiwoom.research.boxevaluation.model.BoxResearchDataset;
import com.example.kiwoom.research.boxevaluation.repository.A1DatasetRepository;
import com.example.kiwoom.research.boxevaluation.service.BoxEvaluationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

/** The single production operation for creating the immutable Discovery A1 unit. */
@Service
public class A1GenerationService {
    private static final String CREATED_BY = "TASK-004-A1";

    private final A1SamplingService sampling;
    private final A1DatasetService datasets;
    private final A1DatasetRepository datasetRepository;
    private final BoxEvaluationService evaluation;
    private final com.example.kiwoom.research.boxevaluation.repository.BoxEvaluationRepository
            evaluationRepository;
    private final DatabaseClient database;
    private final ObjectMapper objectMapper;

    public A1GenerationService(
            A1SamplingService sampling,
            A1DatasetService datasets,
            A1DatasetRepository datasetRepository,
            BoxEvaluationService evaluation,
            com.example.kiwoom.research.boxevaluation.repository.BoxEvaluationRepository
                    evaluationRepository,
            DatabaseClient database,
            ObjectMapper objectMapper) {
        this.sampling = sampling;
        this.datasets = datasets;
        this.datasetRepository = datasetRepository;
        this.evaluation = evaluation;
        this.evaluationRepository = evaluationRepository;
        this.database = database;
        this.objectMapper = objectMapper;
    }

    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<A1GenerationResult> generateA1() {
        return datasetRepository
                .find(A1DatasetService.DATASET_KEY)
                .flatMap(this::reuseCompleteOrFail)
                .switchIfEmpty(Mono.defer(this::createA1));
    }

    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<A1GenerationResult> correctExistingA1EligibilityEvidence() {
        return datasetRepository
                .find(A1DatasetService.DATASET_KEY)
                .switchIfEmpty(Mono.error(new A1SamplingException("A1 dataset is missing")))
                .flatMap(
                        dataset ->
                                parseSamples(dataset)
                                        .flatMap(
                                                samples ->
                                                        sampling.refreshEligibilityEvidence(
                                                                samples))
                                        .flatMap(
                                                enriched -> {
                                                    String manifest = datasets.serialize(enriched);
                                                    Mono<BoxResearchDataset> updated =
                                                            dataset.samplingPolicyJson()
                                                                            .equals(manifest)
                                                                    ? Mono.just(dataset)
                                                                    : datasetRepository
                                                                            .updateSamplingPolicy(
                                                                                    dataset.id(),
                                                                                    manifest);
                                                    return updated.flatMap(
                                                            result ->
                                                                    verify(result, null, enriched));
                                                }));
    }

    private Mono<A1GenerationResult> createA1() {
        return sampling.preview()
                .flatMap(this::validateSamples)
                .flatMap(
                        samples ->
                                datasets.createOrGet(samples)
                                        .flatMap(dataset -> createBatch(dataset, samples)));
    }

    private Mono<A1GenerationResult> createBatch(
            BoxResearchDataset dataset, List<A1Sample> samples) {
        return defaultStrategyId()
                .switchIfEmpty(Mono.error(new A1SamplingException("no strategy definition exists")))
                .flatMap(
                        strategyId -> {
                            List<CreateBoxEvaluationBatchRequest.Item> items =
                                    samples.stream()
                                            .map(
                                                    sample ->
                                                            new CreateBoxEvaluationBatchRequest
                                                                    .Item(
                                                                    sample.code(),
                                                                    sample.cutoffDate()))
                                            .toList();
                            CreateBoxEvaluationBatchRequest request =
                                    new CreateBoxEvaluationBatchRequest(
                                            strategyId,
                                            A1DatasetService.DATASET_KEY,
                                            A1DatasetService.DATASET_KEY,
                                            CREATED_BY,
                                            items);
                            return evaluation
                                    .createBatch(request)
                                    .flatMap(
                                            batch ->
                                                    datasetRepository
                                                            .linkSourceBatch(
                                                                    dataset.id(), batch.id())
                                                            .flatMap(
                                                                    linked ->
                                                                            verify(
                                                                                    linked, batch,
                                                                                    samples)));
                        });
    }

    private Mono<A1GenerationResult> reuseCompleteOrFail(BoxResearchDataset dataset) {
        if (!A1DatasetService.DATASET_TYPE.equals(dataset.datasetType())) {
            return Mono.error(new A1SamplingException("conflicting A1 dataset type"));
        }
        if (dataset.sourceBatchId() == null) {
            return Mono.error(new A1SamplingException("INCOMPLETE_EXISTING_DATASET"));
        }
        return evaluation
                .items(dataset.sourceBatchId())
                .collectList()
                .flatMap(
                        items ->
                                parseSamples(dataset)
                                        .flatMap(this::validateSamples)
                                        .flatMap(samples -> verify(dataset, null, samples)));
    }

    private Mono<A1GenerationResult> verify(
            BoxResearchDataset dataset, BoxEvaluationBatch batch, List<A1Sample> samples) {
        if (batch == null) {
            return evaluationRepository
                    .findBatch(dataset.sourceBatchId())
                    .switchIfEmpty(Mono.error(new A1SamplingException("A1 batch is missing")))
                    .flatMap(
                            found ->
                                    evaluation
                                            .batches()
                                            .filter(
                                                    candidate ->
                                                            A1DatasetService.DATASET_KEY.equals(
                                                                    candidate.datasetVersion()))
                                            .count()
                                            .flatMap(
                                                    count -> {
                                                        if (count != 1
                                                                || !A1DatasetService.DATASET_KEY
                                                                        .equals(
                                                                                found
                                                                                        .datasetVersion())
                                                                || !BoxEvaluationService
                                                                        .GENERATOR_VERSION
                                                                        .equals(
                                                                                found
                                                                                        .candidateGeneratorVersion())) {
                                                            return Mono.error(
                                                                    new A1SamplingException(
                                                                            "CONFLICTING_OR_INCOMPLETE_A1_DATASET"));
                                                        }
                                                        return evaluation
                                                                .items(found.id())
                                                                .collectList()
                                                                .flatMap(
                                                                        items ->
                                                                                verify(
                                                                                        dataset,
                                                                                        found,
                                                                                        samples,
                                                                                        items));
                                                    }));
        }
        return evaluation
                .items(batch.id())
                .collectList()
                .flatMap(items -> verify(dataset, batch, samples, items));
    }

    private Mono<A1GenerationResult> verify(
            BoxResearchDataset dataset,
            BoxEvaluationBatch batch,
            List<A1Sample> samples,
            List<BoxEvaluationItem> items) {
        Set<String> sampleKeys = samples.stream().map(A1Sample::code).collect(Collectors.toSet());
        Set<String> itemKeys =
                items.stream().map(BoxEvaluationItem::code).collect(Collectors.toSet());
        boolean valid =
                samples.size() == A1Sampler.MARKET_QUOTA * 2
                        && items.size() == samples.size()
                        && sampleKeys.size() == samples.size()
                        && itemKeys.equals(sampleKeys)
                        && batch != null
                        && A1DatasetService.DATASET_KEY.equals(batch.datasetVersion())
                        && items.stream().allMatch(item -> item.status().name().equals("PENDING"))
                        && items.stream().map(BoxEvaluationItem::displayOrder).distinct().count()
                                == items.size();
        if (!valid) {
            return Mono.error(new A1SamplingException("A1 structural verification failed"));
        }
        return reactor.core.publisher.Flux.fromIterable(items)
                .flatMap(item -> evaluationRepository.findCandidates(item.id()).hasElements())
                .all(Boolean::booleanValue)
                .flatMap(
                        candidatesPresent ->
                                candidatesPresent
                                        ? Mono.just(new A1GenerationResult(dataset, batch, samples))
                                        : Mono.error(
                                                new A1SamplingException(
                                                        "A1 candidate persistence is incomplete")));
    }

    private Mono<List<A1Sample>> validateSamples(List<A1Sample> samples) {
        if (samples == null || samples.size() != A1Sampler.MARKET_QUOTA * 2) {
            return Mono.error(new A1SamplingException("A1 sample size is not 20"));
        }
        if (samples.stream().map(A1Sample::code).distinct().count() != samples.size()) {
            return Mono.error(new A1SamplingException("A1 symbols are not unique"));
        }
        long kospi = samples.stream().filter(sample -> "KOSPI".equals(sample.market())).count();
        long kosdaq = samples.stream().filter(sample -> "KOSDAQ".equals(sample.market())).count();
        if (kospi != A1Sampler.MARKET_QUOTA || kosdaq != A1Sampler.MARKET_QUOTA) {
            return Mono.error(new A1SamplingException("A1 market quota is invalid"));
        }
        if (samples.stream().anyMatch(sample -> sample.cutoffDate() == null)) {
            return Mono.error(new A1SamplingException("A1 cutoff date is required"));
        }
        if (samples.stream()
                .anyMatch(
                        sample ->
                                !"TARGET_REACHED".equals(sample.historicalBackfillStatus())
                                        || sample.minimumContextCandles()
                                                != A1Sampler.CONTEXT_CANDLES
                                        || sample.actualContextCandleCount()
                                                < A1Sampler.CONTEXT_CANDLES
                                        || !sample.cutoffIsActualTradingDate())) {
            return Mono.error(new A1SamplingException("A1 eligibility evidence is incomplete"));
        }
        return Mono.just(samples);
    }

    private Mono<Long> defaultStrategyId() {
        return database.sql("SELECT id FROM strategy_definition ORDER BY id LIMIT 1")
                .map((row, metadata) -> ((Number) row.get("id")).longValue())
                .one();
    }

    private Mono<List<A1Sample>> parseSamples(BoxResearchDataset dataset) {
        try {
            A1DatasetManifest manifest =
                    objectMapper.readValue(dataset.samplingPolicyJson(), A1DatasetManifest.class);
            if (!A1DatasetService.STAGE.equals(manifest.stage())
                    || manifest.seed() != A1Sampler.SEED
                    || !A1Sampler.ALGORITHM_VERSION.equals(manifest.algorithmVersion())) {
                return Mono.error(new A1SamplingException("conflicting A1 metadata"));
            }
            return Mono.just(manifest.samples());
        } catch (JsonProcessingException exception) {
            return Mono.error(new A1SamplingException("invalid A1 manifest"));
        }
    }
}
