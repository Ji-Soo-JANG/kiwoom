package com.example.kiwoom.research.boxevaluation.a1;

import com.example.kiwoom.research.boxevaluation.model.BoxResearchDataset;
import com.example.kiwoom.research.boxevaluation.repository.A1DatasetRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class A1DatasetService {
    public static final String DATASET_KEY = "BOX-FORMATION-DISCOVERY-A1";
    public static final String DATASET_TYPE = "DISCOVERY";
    public static final String STAGE = "A1";
    public static final String BLIND_POLICY_VERSION = "a1-blind-v1";
    public static final String FEATURE_SNAPSHOT_VERSION = "none-a1";
    private final A1DatasetRepository repository;
    private final ObjectMapper objectMapper;

    public A1DatasetService(A1DatasetRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /** Persists a manifest only when explicitly invoked by a future Gate 1 flow. */
    public Mono<BoxResearchDataset> createOrGet(List<A1Sample> samples) {
        String manifest = serialize(samples);
        return repository.createOrGet(
                DATASET_KEY, manifest, BLIND_POLICY_VERSION, FEATURE_SNAPSHOT_VERSION);
    }

    public String serialize(List<A1Sample> samples) {
        try {
            return objectMapper.writeValueAsString(
                    new A1DatasetManifest(
                            DATASET_KEY,
                            DATASET_TYPE,
                            STAGE,
                            A1Sampler.SEED,
                            A1Sampler.ALGORITHM_VERSION,
                            samples));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("A1 manifest cannot be serialized", exception);
        }
    }
}
