package com.example.kiwoom.research.boxevaluation.repository;

import com.example.kiwoom.research.boxevaluation.model.BoxResearchDataset;
import org.springframework.r2dbc.BadSqlGrammarException;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/** Idempotent persistence boundary for A1 dataset metadata. */
@Repository
public class A1DatasetRepository {
    private final DatabaseClient database;

    public A1DatasetRepository(DatabaseClient database) {
        this.database = database;
    }

    public Mono<BoxResearchDataset> createOrGet(
            String datasetKey,
            String samplingPolicyJson,
            String blindPolicyVersion,
            String featureSnapshotVersion) {
        return database.sql(
                        """
                        INSERT INTO box_research_dataset(
                            dataset_key, dataset_type, source_batch_id,
                            sampling_policy_json, blind_policy_version, feature_snapshot_version)
                        VALUES (:key, 'DISCOVERY', NULL, :sampling, :blind, :features)
                        ON CONFLICT (dataset_key) DO NOTHING
                        """)
                .bind("key", datasetKey)
                .bind("sampling", samplingPolicyJson)
                .bind("blind", blindPolicyVersion)
                .bind("features", featureSnapshotVersion)
                .fetch()
                .rowsUpdated()
                .then(find(datasetKey))
                .onErrorResume(
                        BadSqlGrammarException.class,
                        error ->
                                insertWithoutConflict(
                                        datasetKey,
                                        samplingPolicyJson,
                                        blindPolicyVersion,
                                        featureSnapshotVersion));
    }

    private Mono<BoxResearchDataset> insertWithoutConflict(
            String datasetKey,
            String samplingPolicyJson,
            String blindPolicyVersion,
            String featureSnapshotVersion) {
        return find(datasetKey)
                .switchIfEmpty(
                        database.sql(
                                        """
INSERT INTO box_research_dataset(
    dataset_key, dataset_type, source_batch_id,
    sampling_policy_json, blind_policy_version, feature_snapshot_version)
SELECT :key, 'DISCOVERY', NULL, :sampling, :blind, :features
WHERE NOT EXISTS (
    SELECT 1 FROM box_research_dataset WHERE dataset_key=:key)
""")
                                .bind("key", datasetKey)
                                .bind("sampling", samplingPolicyJson)
                                .bind("blind", blindPolicyVersion)
                                .bind("features", featureSnapshotVersion)
                                .fetch()
                                .rowsUpdated()
                                .then(find(datasetKey)));
    }

    public Mono<BoxResearchDataset> find(String datasetKey) {
        return database.sql("SELECT * FROM box_research_dataset WHERE dataset_key=:key")
                .bind("key", datasetKey)
                .map(
                        (row, metadata) ->
                                new BoxResearchDataset(
                                        ((Number) row.get("id")).longValue(),
                                        row.get("dataset_key", String.class),
                                        row.get("dataset_type", String.class),
                                        row.get("source_batch_id", Long.class),
                                        row.get("sampling_policy_json", String.class),
                                        row.get("blind_policy_version", String.class),
                                        row.get("feature_snapshot_version", String.class),
                                        row.get("created_at", java.time.Instant.class)))
                .one();
    }

    public Mono<BoxResearchDataset> linkSourceBatch(long datasetId, long batchId) {
        return database.sql(
                        "UPDATE box_research_dataset SET source_batch_id=:batch WHERE id=:dataset")
                .bind("batch", batchId)
                .bind("dataset", datasetId)
                .fetch()
                .rowsUpdated()
                .flatMap(updated -> updated == 1 ? findById(datasetId) : Mono.empty());
    }

    public Mono<BoxResearchDataset> updateSamplingPolicy(
            long datasetId, String samplingPolicyJson) {
        return database.sql(
                        "UPDATE box_research_dataset SET sampling_policy_json=:sampling WHERE id=:id")
                .bind("sampling", samplingPolicyJson)
                .bind("id", datasetId)
                .fetch()
                .rowsUpdated()
                .flatMap(updated -> updated == 1 ? findById(datasetId) : Mono.empty());
    }

    private Mono<BoxResearchDataset> findById(long datasetId) {
        return database.sql("SELECT * FROM box_research_dataset WHERE id=:id")
                .bind("id", datasetId)
                .map(
                        (row, metadata) ->
                                new BoxResearchDataset(
                                        ((Number) row.get("id")).longValue(),
                                        row.get("dataset_key", String.class),
                                        row.get("dataset_type", String.class),
                                        row.get("source_batch_id", Long.class),
                                        row.get("sampling_policy_json", String.class),
                                        row.get("blind_policy_version", String.class),
                                        row.get("feature_snapshot_version", String.class),
                                        row.get("created_at", java.time.Instant.class)))
                .one();
    }
}
