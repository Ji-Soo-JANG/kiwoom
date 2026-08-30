package com.example.kiwoom.research.boxevaluation.repository;

import com.example.kiwoom.dto.StoredDailyCandle;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import io.r2dbc.spi.Row;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class BoxEvaluationRepository {
    private final DatabaseClient database;

    public BoxEvaluationRepository(DatabaseClient database) {
        this.database = database;
    }

    public Mono<BoxEvaluationBatch> createBatch(BoxEvaluationBatch batch) {
        return database.sql(
                        """
                INSERT INTO box_evaluation_batch(strategy_version_id, name, dataset_version,
                    candidate_generator_version, sampling_policy_json, blind_policy_version,
                    status, created_by)
                VALUES (:strategy, :name, :dataset, :generator, :sampling, :blind, :status, :creator)
                """)
                .bind("strategy", batch.strategyVersionId())
                .bind("name", batch.name())
                .bind("dataset", batch.datasetVersion())
                .bind("generator", batch.candidateGeneratorVersion())
                .bind("sampling", batch.samplingPolicyJson())
                .bind("blind", batch.blindPolicyVersion())
                .bind("status", batch.status().name())
                .bind("creator", batch.createdBy())
                .filter(statement -> statement.returnGeneratedValues("id"))
                .map(row -> number(row.get("id")))
                .one()
                .flatMap(this::findBatch);
    }

    public Mono<BoxEvaluationBatch> findBatch(long id) {
        return database.sql("SELECT * FROM box_evaluation_batch WHERE id=:id")
                .bind("id", id)
                .map(
                        row ->
                                new BoxEvaluationBatch(
                                        number(row.get("id")),
                                        number(row.get("strategy_version_id")),
                                        row.get("name", String.class),
                                        row.get("dataset_version", String.class),
                                        row.get("candidate_generator_version", String.class),
                                        row.get("sampling_policy_json", String.class),
                                        row.get("blind_policy_version", String.class),
                                        BoxEvaluationBatchStatus.valueOf(
                                                row.get("status", String.class)),
                                        row.get("created_by", String.class),
                                        instant(row.get("created_at"))))
                .one();
    }

    public Flux<BoxEvaluationBatch> findBatches() {
        return database.sql("SELECT * FROM box_evaluation_batch ORDER BY created_at DESC, id DESC")
                .map(
                        row ->
                                new BoxEvaluationBatch(
                                        number(row.get("id")),
                                        number(row.get("strategy_version_id")),
                                        row.get("name", String.class),
                                        row.get("dataset_version", String.class),
                                        row.get("candidate_generator_version", String.class),
                                        row.get("sampling_policy_json", String.class),
                                        row.get("blind_policy_version", String.class),
                                        BoxEvaluationBatchStatus.valueOf(
                                                row.get("status", String.class)),
                                        row.get("created_by", String.class),
                                        instant(row.get("created_at"))))
                .all();
    }

    public Flux<StoredDailyCandle> findBlindCandles(long itemId) {
        return database.sql(
                        """
                SELECT c.code, c.trade_date, c.open_price, c.high_price, c.low_price,
                       c.close_price, c.volume
                FROM daily_candle c
                JOIN box_evaluation_item i ON i.code=c.code
                WHERE i.id=:item AND c.trade_date <= i.cutoff_date
                ORDER BY c.trade_date
                """)
                .bind("item", itemId)
                .map(
                        row ->
                                new StoredDailyCandle(
                                        row.get("code", String.class),
                                        row.get("trade_date", LocalDate.class),
                                        number(row.get("open_price")),
                                        number(row.get("high_price")),
                                        number(row.get("low_price")),
                                        number(row.get("close_price")),
                                        number(row.get("volume"))))
                .all();
    }

    public Flux<StoredDailyCandle> findBlindCandlesFor(String code, LocalDate cutoff) {
        return database.sql(
                        """
                SELECT code, trade_date, open_price, high_price, low_price, close_price, volume
                FROM daily_candle
                WHERE code=:code AND trade_date <= :cutoff
                ORDER BY trade_date
                """)
                .bind("code", code)
                .bind("cutoff", cutoff)
                .map(
                        row ->
                                new StoredDailyCandle(
                                        row.get("code", String.class),
                                        row.get("trade_date", LocalDate.class),
                                        number(row.get("open_price")),
                                        number(row.get("high_price")),
                                        number(row.get("low_price")),
                                        number(row.get("close_price")),
                                        number(row.get("volume"))))
                .all();
    }

    public Flux<StoredDailyCandle> findOutcomeCandles(long itemId, int limit) {
        return database.sql(
                        """
                SELECT c.code, c.trade_date, c.open_price, c.high_price, c.low_price,
                       c.close_price, c.volume
                FROM daily_candle c JOIN box_evaluation_item i ON i.code=c.code
                WHERE i.id=:item AND c.trade_date > i.cutoff_date
                ORDER BY c.trade_date LIMIT :limit
                """)
                .bind("item", itemId)
                .bind("limit", limit)
                .map(
                        row ->
                                new StoredDailyCandle(
                                        row.get("code", String.class),
                                        row.get("trade_date", LocalDate.class),
                                        number(row.get("open_price")),
                                        number(row.get("high_price")),
                                        number(row.get("low_price")),
                                        number(row.get("close_price")),
                                        number(row.get("volume"))))
                .all();
    }

    public Mono<BoxEvaluationItem> createItem(BoxEvaluationItem item) {
        DatabaseClient.GenericExecuteSpec query =
                database.sql(
                                """
                INSERT INTO box_evaluation_item(batch_id, code, cutoff_date, display_order,
                    source_scan_id, data_hash, status)
                VALUES (:batch, :code, :cutoff, :displayOrder, :scan, :dataHash, :status)
                """)
                        .bind("batch", item.batchId())
                        .bind("code", item.code())
                        .bind("cutoff", item.cutoffDate())
                        .bind("displayOrder", item.displayOrder())
                        .bind("dataHash", item.dataHash())
                        .bind("status", item.status().name());
        query = bindNullable(query, "scan", item.sourceScanId(), Long.class);
        return query.filter(statement -> statement.returnGeneratedValues("id"))
                .map(row -> number(row.get("id")))
                .one()
                .flatMap(this::findItem);
    }

    public Mono<BoxEvaluationItem> findItem(long id) {
        return database.sql("SELECT * FROM box_evaluation_item WHERE id=:id")
                .bind("id", id)
                .map(
                        row ->
                                new BoxEvaluationItem(
                                        number(row.get("id")),
                                        number(row.get("batch_id")),
                                        row.get("code", String.class),
                                        row.get("cutoff_date", LocalDate.class),
                                        ((Number) row.get("display_order")).intValue(),
                                        nullableNumber(row.get("source_scan_id")),
                                        row.get("data_hash", String.class),
                                        BoxEvaluationItemStatus.valueOf(
                                                row.get("status", String.class)),
                                        number(row.get("lock_version")),
                                        instant(row.get("created_at"))))
                .one();
    }

    public Mono<BoxEvaluationItem> findNext(long batchId) {
        return database.sql(
                        """
                SELECT * FROM box_evaluation_item
                WHERE batch_id=:batch AND status IN ('PENDING', 'DRAFTED')
                ORDER BY display_order LIMIT 1
                """)
                .bind("batch", batchId)
                .map(
                        row ->
                                new BoxEvaluationItem(
                                        number(row.get("id")),
                                        number(row.get("batch_id")),
                                        row.get("code", String.class),
                                        row.get("cutoff_date", LocalDate.class),
                                        ((Number) row.get("display_order")).intValue(),
                                        nullableNumber(row.get("source_scan_id")),
                                        row.get("data_hash", String.class),
                                        BoxEvaluationItemStatus.valueOf(
                                                row.get("status", String.class)),
                                        number(row.get("lock_version")),
                                        instant(row.get("created_at"))))
                .one();
    }

    public Flux<BoxEvaluationItem> findItems(long batchId) {
        return database.sql("SELECT * FROM box_evaluation_item WHERE batch_id=:batch ORDER BY display_order")
                .bind("batch", batchId)
                .map((row, metadata) -> item(row))
                .all();
    }

    private BoxEvaluationItem item(Row row) {
        return new BoxEvaluationItem(
                number(row.get("id")),
                number(row.get("batch_id")),
                row.get("code", String.class),
                row.get("cutoff_date", LocalDate.class),
                ((Number) row.get("display_order")).intValue(),
                nullableNumber(row.get("source_scan_id")),
                row.get("data_hash", String.class),
                BoxEvaluationItemStatus.valueOf(row.get("status", String.class)),
                number(row.get("lock_version")),
                instant(row.get("created_at")));
    }

    public Mono<BoxEvaluationProgress> progress(long batchId) {
        return database.sql(
                        "SELECT :batch AS batch_id, COUNT(*) AS total, SUM(CASE WHEN status='COMMITTED' THEN 1 ELSE 0 END) AS completed, MIN(CASE WHEN status IN ('PENDING','DRAFTED') THEN id END) AS next_item_id FROM box_evaluation_item WHERE batch_id=:batch")
                .bind("batch", batchId)
                .map(
                        (row, metadata) ->
                                new BoxEvaluationProgress(
                                        batchId,
                                        number(row.get("completed")),
                                        number(row.get("total")),
                                        nullableNumber(row.get("next_item_id"))))
                .one();
    }

    public Mono<Long> markFormationComplete(long itemId) {
        return database.sql(
                        "UPDATE box_evaluation_item SET status='COMMITTED', updated_at=CURRENT_TIMESTAMP WHERE id=:item")
                .bind("item", itemId)
                .fetch()
                .rowsUpdated();
    }

    public Mono<BoxEvaluationCandidate> addCandidate(BoxEvaluationCandidate candidate) {
        return database.sql(
                        """
                INSERT INTO box_evaluation_candidate(item_id, candidate_key, start_date, end_date,
                    rank_no, feature_json, generator_version)
                SELECT :item, :key, :startDate, :endDate, :rank, :features, :generator
                FROM box_evaluation_item i
                WHERE i.id=:item AND :startDate <= :endDate AND :endDate <= i.cutoff_date
                """)
                .bind("item", candidate.itemId())
                .bind("key", candidate.candidateKey())
                .bind("startDate", candidate.startDate())
                .bind("endDate", candidate.endDate())
                .bind("rank", candidate.rankNo())
                .bind("features", candidate.featureJson())
                .bind("generator", candidate.generatorVersion())
                .filter(statement -> statement.returnGeneratedValues("id"))
                .map(row -> number(row.get("id")))
                .one()
                .switchIfEmpty(
                        Mono.error(new IllegalArgumentException("후보 구간은 cutoff를 넘을 수 없습니다.")))
                .map(id -> candidateWithId(candidate, id));
    }

    public Flux<BoxEvaluationCandidate> findCandidates(long itemId) {
        return database.sql(
                        "SELECT * FROM box_evaluation_candidate WHERE item_id=:item ORDER BY rank_no")
                .bind("item", itemId)
                .map(
                        row ->
                                new BoxEvaluationCandidate(
                                        number(row.get("id")),
                                        number(row.get("item_id")),
                                        row.get("candidate_key", String.class),
                                        row.get("start_date", LocalDate.class),
                                        row.get("end_date", LocalDate.class),
                                        ((Number) row.get("rank_no")).intValue(),
                                        row.get("feature_json", String.class),
                                        row.get("generator_version", String.class)))
                .all();
    }

    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<BoxEvaluationDraft> saveDraft(BoxEvaluationDraft draft, long expectedRevision) {
        DatabaseClient.GenericExecuteSpec update =
                database.sql(
                                """
                UPDATE box_evaluation_draft d SET boundary_decision=:decision,
                    selected_candidate_key=:candidate,
                    edited_start_date=:startDate, edited_end_date=:endDate, label_code=:label,
                    confidence=:confidence, reason_codes=:reasons, comment_text=:comment,
                    draft_revision=draft_revision+1, updated_at=CURRENT_TIMESTAMP
                WHERE d.item_id=:item AND d.reviewer_id=:reviewer
                  AND d.draft_revision=:revision
                  AND EXISTS (SELECT 1 FROM box_evaluation_item i WHERE i.id=d.item_id
                    AND i.status IN ('PENDING','DRAFTED')
                    AND (:startDate IS NULL OR :endDate IS NULL
                         OR (:startDate <= :endDate AND :endDate <= i.cutoff_date)))
                """)
                        .bind("item", draft.itemId())
                        .bind("reviewer", draft.reviewerId())
                        .bind("revision", expectedRevision)
                        .bind("reasons", draft.reasonCodes());
        update = bindDraftValues(update, draft);
        return update.fetch()
                .rowsUpdated()
                .flatMap(
                        rows -> {
                            if (rows > 0) return findDraft(draft.itemId(), draft.reviewerId());
                            if (expectedRevision == 0)
                                return insertDraft(draft)
                                        .then(findDraft(draft.itemId(), draft.reviewerId()));
                            return Mono.error(new IllegalStateException("임시 평가가 다른 세션에서 변경되었습니다."));
                        });
    }

    public Mono<BoxEvaluationDraft> findDraft(long itemId, String reviewerId) {
        return database.sql(
                        "SELECT * FROM box_evaluation_draft WHERE item_id=:item AND reviewer_id=:reviewer")
                .bind("item", itemId)
                .bind("reviewer", reviewerId)
                .map(
                        row ->
                                new BoxEvaluationDraft(
                                        number(row.get("id")),
                                        number(row.get("item_id")),
                                        row.get("reviewer_id", String.class),
                                        boundaryDecision(
                                                row.get("boundary_decision", String.class)),
                                        row.get("selected_candidate_key", String.class),
                                        row.get("edited_start_date", LocalDate.class),
                                        row.get("edited_end_date", LocalDate.class),
                                        row.get("label_code", String.class),
                                        nullableInteger(row.get("confidence")),
                                        row.get("reason_codes", String.class),
                                        row.get("comment_text", String.class),
                                        number(row.get("draft_revision")),
                                        instant(row.get("updated_at"))))
                .one();
    }

    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<BoxEvaluation> commit(BoxEvaluation evaluation) {
        return findByCommitKey(evaluation.commitKey())
                .switchIfEmpty(
                        findCommittedEvaluation(evaluation.itemId(), evaluation.reviewerId())
                                .flatMap(existing -> updateEvaluation(existing.id(), evaluation))
                                .switchIfEmpty(transitionToCommitted(evaluation.itemId())
                                .flatMap(
                                        changed ->
                                                changed == 1
                                                        ? insertEvaluation(evaluation)
                                                        : Mono.error(
                                                                new IllegalStateException(
                                                                        "확정할 수 없는 평가 항목 상태입니다.")))));
    }

    private Mono<BoxEvaluation> updateEvaluation(long evaluationId, BoxEvaluation evaluation) {
        DatabaseClient.GenericExecuteSpec query = database.sql(
                """
                UPDATE box_evaluation SET commit_key=:commitKey, boundary_decision=:decision,
                    selected_candidate_key=:candidate, final_start_date=:startDate, final_end_date=:endDate,
                    label_code=:label, confidence=:confidence, reason_codes=:reasons, comment_text=:comment,
                    input_snapshot_json=:snapshot, evaluation_schema_version=:schema,
                    committed_at=CURRENT_TIMESTAMP WHERE id=:id
                """)
                .bind("id", evaluationId)
                .bind("commitKey", evaluation.commitKey())
                .bind("decision", evaluation.boundaryDecision().name())
                .bind("label", evaluation.labelCode())
                .bind("confidence", evaluation.confidence())
                .bind("reasons", evaluation.reasonCodes())
                .bind("snapshot", evaluation.inputSnapshotJson())
                .bind("schema", evaluation.evaluationSchemaVersion());
        query = bindNullable(query, "candidate", evaluation.selectedCandidateKey(), String.class);
        query = bindNullable(query, "startDate", evaluation.finalStartDate(), LocalDate.class);
        query = bindNullable(query, "endDate", evaluation.finalEndDate(), LocalDate.class);
        query = bindNullable(query, "comment", evaluation.comment(), String.class);
        return query.fetch().rowsUpdated().flatMap(updated -> updated == 1 ? findEvaluation(evaluationId) : Mono.empty());
    }

    public Mono<BoxEvaluationSupersede> supersede(BoxEvaluationSupersede supersede) {
        return database.sql(
                        """
                INSERT INTO box_evaluation_supersede(evaluation_id, superseded_by_evaluation_id,
                    reason, superseded_by)
                VALUES (:evaluation, :replacement, :reason, :actor)
                """)
                .bind("evaluation", supersede.evaluationId())
                .bind("replacement", supersede.supersededByEvaluationId())
                .bind("reason", supersede.reason())
                .bind("actor", supersede.supersededBy())
                .filter(statement -> statement.returnGeneratedValues("id", "superseded_at"))
                .map(
                        row ->
                                new BoxEvaluationSupersede(
                                        number(row.get("id")),
                                        supersede.evaluationId(),
                                        supersede.supersededByEvaluationId(),
                                        supersede.reason(),
                                        supersede.supersededBy(),
                                        instant(row.get("superseded_at"))))
                .one();
    }

    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<BoxEvaluationReveal> reveal(BoxEvaluationReveal reveal) {
        return database.sql(
                        """
                INSERT INTO box_evaluation_reveal(evaluation_id, outcome_policy_version,
                    requested_by, outcome_snapshot_json)
                SELECT :evaluation, :policy, :actor, :outcome FROM box_evaluation e
                JOIN box_evaluation_item i ON i.id=e.item_id
                WHERE e.id=:evaluation AND i.status='COMMITTED'
                """)
                .bind("evaluation", reveal.evaluationId())
                .bind("policy", reveal.outcomePolicyVersion())
                .bind("actor", reveal.requestedBy())
                .bind("outcome", reveal.outcomeSnapshotJson())
                .filter(statement -> statement.returnGeneratedValues("id", "revealed_at"))
                .map(
                        row ->
                                new BoxEvaluationReveal(
                                        number(row.get("id")),
                                        reveal.evaluationId(),
                                        reveal.outcomePolicyVersion(),
                                        reveal.requestedBy(),
                                        reveal.outcomeSnapshotJson(),
                                        instant(row.get("revealed_at"))))
                .one()
                .switchIfEmpty(Mono.error(new IllegalStateException("확정된 평가만 공개할 수 있습니다.")))
                .flatMap(
                        saved ->
                                database.sql(
                                                "UPDATE box_evaluation_item SET status='REVEALED', "
                                                        + "lock_version=lock_version+1, updated_at=CURRENT_TIMESTAMP "
                                                        + "WHERE id=(SELECT item_id FROM box_evaluation WHERE id=:evaluation)")
                                        .bind("evaluation", reveal.evaluationId())
                                        .fetch()
                                        .rowsUpdated()
                                        .thenReturn(saved));
    }

    private Mono<Void> insertDraft(BoxEvaluationDraft draft) {
        DatabaseClient.GenericExecuteSpec insert =
                database.sql(
                                """
                INSERT INTO box_evaluation_draft(item_id, reviewer_id, boundary_decision,
                    selected_candidate_key,
                    edited_start_date, edited_end_date, label_code, confidence, reason_codes,
                    comment_text, draft_revision)
                SELECT :item, :reviewer, :decision, :candidate, :startDate, :endDate, :label, :confidence,
                    :reasons, :comment, 1 FROM box_evaluation_item i
                WHERE i.id=:item AND i.status IN ('PENDING','DRAFTED')
                  AND (:startDate IS NULL OR :endDate IS NULL
                       OR (:startDate <= :endDate AND :endDate <= i.cutoff_date))
                """)
                        .bind("item", draft.itemId())
                        .bind("reviewer", draft.reviewerId())
                        .bind("reasons", draft.reasonCodes());
        insert = bindDraftValues(insert, draft);
        return insert.fetch()
                .rowsUpdated()
                .flatMap(
                        rows ->
                                rows == 1
                                        ? database.sql(
                                                        "UPDATE box_evaluation_item SET status='DRAFTED', "
                                                                + "lock_version=lock_version+1, updated_at=CURRENT_TIMESTAMP "
                                                                + "WHERE id=:item AND status='PENDING'")
                                                .bind("item", draft.itemId())
                                                .then()
                                        : Mono.error(
                                                new IllegalArgumentException(
                                                        "임시 평가 구간은 cutoff를 넘을 수 없습니다.")));
    }

    private DatabaseClient.GenericExecuteSpec bindDraftValues(
            DatabaseClient.GenericExecuteSpec query, BoxEvaluationDraft draft) {
        query =
                bindNullable(
                        query,
                        "decision",
                        draft.boundaryDecision() == null ? null : draft.boundaryDecision().name(),
                        String.class);
        query = bindNullable(query, "candidate", draft.selectedCandidateKey(), String.class);
        query = bindNullable(query, "startDate", draft.editedStartDate(), LocalDate.class);
        query = bindNullable(query, "endDate", draft.editedEndDate(), LocalDate.class);
        query = bindNullable(query, "label", draft.labelCode(), String.class);
        query = bindNullable(query, "confidence", draft.confidence(), Integer.class);
        return bindNullable(query, "comment", draft.comment(), String.class);
    }

    private Mono<Long> transitionToCommitted(long itemId) {
        return database.sql(
                        """
                UPDATE box_evaluation_item SET status='COMMITTED', lock_version=lock_version+1,
                    updated_at=CURRENT_TIMESTAMP
                WHERE id=:item AND status IN ('PENDING','DRAFTED')
                """)
                .bind("item", itemId)
                .fetch()
                .rowsUpdated();
    }

    public Mono<Long> closeBatchIfComplete(long batchId) {
        return database.sql(
                        """
                UPDATE box_evaluation_batch b SET status='CLOSED', updated_at=CURRENT_TIMESTAMP
                WHERE b.id=:batch AND b.status IN ('READY','IN_PROGRESS')
                  AND EXISTS (SELECT 1 FROM box_evaluation_item i WHERE i.batch_id=b.id)
                  AND NOT EXISTS (SELECT 1 FROM box_evaluation_item i WHERE i.batch_id=b.id
                                  AND i.status IN ('PENDING','DRAFTED'))
                """)
                .bind("batch", batchId)
                .fetch()
                .rowsUpdated();
    }

    private Mono<BoxEvaluation> insertEvaluation(BoxEvaluation evaluation) {
        DatabaseClient.GenericExecuteSpec query =
                database.sql(
                                """
                INSERT INTO box_evaluation(item_id, reviewer_id, commit_key, boundary_decision,
                    selected_candidate_key, final_start_date, final_end_date, label_code,
                    confidence, reason_codes, comment_text, input_snapshot_json,
                    evaluation_schema_version)
                SELECT :item, :reviewer, :commitKey, :decision, :candidate, :startDate, :endDate, :label,
                    :confidence, :reasons, :comment, :snapshot, :schema
                FROM box_evaluation_item i WHERE i.id=:item AND i.status='COMMITTED'
                  AND (:startDate IS NULL OR :endDate IS NULL
                       OR (:startDate <= :endDate AND :endDate <= i.cutoff_date))
                """)
                        .bind("item", evaluation.itemId())
                        .bind("reviewer", evaluation.reviewerId())
                        .bind("commitKey", evaluation.commitKey())
                        .bind("decision", evaluation.boundaryDecision().name())
                        .bind("label", evaluation.labelCode())
                        .bind("confidence", evaluation.confidence())
                        .bind("reasons", evaluation.reasonCodes())
                        .bind("snapshot", evaluation.inputSnapshotJson())
                        .bind("schema", evaluation.evaluationSchemaVersion());
        query = bindNullable(query, "candidate", evaluation.selectedCandidateKey(), String.class);
        query = bindNullable(query, "startDate", evaluation.finalStartDate(), LocalDate.class);
        query = bindNullable(query, "endDate", evaluation.finalEndDate(), LocalDate.class);
        query = bindNullable(query, "comment", evaluation.comment(), String.class);
        return query.filter(statement -> statement.returnGeneratedValues("id"))
                .map(row -> number(row.get("id")))
                .one()
                .switchIfEmpty(
                        Mono.error(new IllegalArgumentException("확정 구간은 cutoff를 넘을 수 없습니다.")))
                .flatMap(this::findEvaluation);
    }

    private Mono<BoxEvaluation> findByCommitKey(String commitKey) {
        return database.sql("SELECT id FROM box_evaluation WHERE commit_key=:key")
                .bind("key", commitKey)
                .map(row -> number(row.get("id")))
                .one()
                .flatMap(this::findEvaluation);
    }

    public Mono<BoxEvaluation> findEvaluation(long id) {
        return database.sql("SELECT * FROM box_evaluation WHERE id=:id")
                .bind("id", id)
                .map(
                        row ->
                                new BoxEvaluation(
                                        number(row.get("id")),
                                        number(row.get("item_id")),
                                        row.get("reviewer_id", String.class),
                                        row.get("commit_key", String.class),
                                        boundaryDecision(
                                                row.get("boundary_decision", String.class)),
                                        row.get("selected_candidate_key", String.class),
                                        row.get("final_start_date", LocalDate.class),
                                        row.get("final_end_date", LocalDate.class),
                                        row.get("label_code", String.class),
                                        ((Number) row.get("confidence")).intValue(),
                                        row.get("reason_codes", String.class),
                                        row.get("comment_text", String.class),
                                        row.get("input_snapshot_json", String.class),
                                        row.get("evaluation_schema_version", String.class),
                                        instant(row.get("committed_at"))))
                .one();
    }

    public Mono<BoxEvaluation> findCommittedEvaluationByItem(long itemId) {
        return database.sql(
                        "SELECT id FROM box_evaluation WHERE item_id=:item ORDER BY committed_at DESC LIMIT 1")
                .bind("item", itemId)
                .map(row -> number(row.get("id")))
                .one()
                .flatMap(this::findEvaluation);
    }

    public Mono<BoxEvaluation> findCommittedEvaluation(long itemId, String reviewerId) {
        return database.sql(
                        "SELECT id FROM box_evaluation WHERE item_id=:item AND reviewer_id=:reviewer ORDER BY committed_at DESC LIMIT 1")
                .bind("item", itemId)
                .bind("reviewer", reviewerId)
                .map(row -> number(row.get("id")))
                .one()
                .flatMap(this::findEvaluation);
    }

    public Mono<BoxEvaluationReveal> findReveal(long evaluationId) {
        return database.sql("SELECT * FROM box_evaluation_reveal WHERE evaluation_id=:evaluation")
                .bind("evaluation", evaluationId)
                .map(
                        row ->
                                new BoxEvaluationReveal(
                                        number(row.get("id")),
                                        number(row.get("evaluation_id")),
                                        row.get("outcome_policy_version", String.class),
                                        row.get("requested_by", String.class),
                                        row.get("outcome_snapshot_json", String.class),
                                        instant(row.get("revealed_at"))))
                .one();
    }

    public Mono<BoxFormationEvaluation> saveFormation(
            BoxFormationEvaluation value, long expectedRevision) {
        DatabaseClient.GenericExecuteSpec query =
                database.sql(
                                """
                INSERT INTO box_formation_evaluation(item_id, reviewer_id, formation_label,
   proposed_start_date, proposed_end_date, final_start_date, final_end_date, period_decision,
   proposed_lower_support_min, proposed_lower_support_max,
   proposed_upper_resistance_min, proposed_upper_resistance_max,
                    final_lower_support_min, final_lower_support_max,
                    final_upper_resistance_min, final_upper_resistance_max, zone_decision, note, confidence,
                    boundary_decision, label_code, reason_codes, comment_text, revision)
                VALUES (:item, :reviewer, :label,
                    (SELECT c.start_date FROM box_evaluation_candidate c WHERE c.item_id=:item AND c.candidate_key='NARROW'),
                    (SELECT c.end_date FROM box_evaluation_candidate c WHERE c.item_id=:item AND c.candidate_key='NARROW'),
   :startDate, :endDate, :periodDecision, :proposedLowerMin, :proposedLowerMax,
   :proposedUpperMin, :proposedUpperMax, :lowerMin, :lowerMax, :upperMin, :upperMax,
   :zoneDecision, :note, :confidence, :boundaryDecision, :labelCode, :reasonCodes, :comment, :revision)
                ON CONFLICT (item_id, reviewer_id) DO UPDATE SET
   formation_label=EXCLUDED.formation_label, final_start_date=EXCLUDED.final_start_date,
   final_end_date=EXCLUDED.final_end_date,
   period_decision=EXCLUDED.period_decision,
   proposed_lower_support_min=EXCLUDED.proposed_lower_support_min,
   proposed_lower_support_max=EXCLUDED.proposed_lower_support_max,
   proposed_upper_resistance_min=EXCLUDED.proposed_upper_resistance_min,
   proposed_upper_resistance_max=EXCLUDED.proposed_upper_resistance_max,
                    final_lower_support_min=EXCLUDED.final_lower_support_min,
                    final_lower_support_max=EXCLUDED.final_lower_support_max,
                    final_upper_resistance_min=EXCLUDED.final_upper_resistance_min,
                    final_upper_resistance_max=EXCLUDED.final_upper_resistance_max,
   zone_decision=EXCLUDED.zone_decision, note=EXCLUDED.note, confidence=EXCLUDED.confidence,
   boundary_decision=EXCLUDED.boundary_decision, label_code=EXCLUDED.label_code,
   reason_codes=EXCLUDED.reason_codes, comment_text=EXCLUDED.comment_text,
   revision=box_formation_evaluation.revision+1,
                    committed_at=CURRENT_TIMESTAMP
                WHERE box_formation_evaluation.revision=:expected
                """)
                        .bind("item", value.itemId())
                        .bind("reviewer", value.reviewerId())
                        .bind("label", value.formationLabel().name())
                        .bind("revision", expectedRevision + 1)
                        .bind("expected", expectedRevision);
        query = bindNullable(query, "startDate", value.finalStartDate(), LocalDate.class);
        query = bindNullable(query, "endDate", value.finalEndDate(), LocalDate.class);
        query = bindNullable(query, "periodDecision", value.periodDecision(), String.class);
        query =
                bindNullable(
                        query,
                        "proposedLowerMin",
                        value.proposedLowerSupportMin(),
                        BigDecimal.class);
        query =
                bindNullable(
                        query,
                        "proposedLowerMax",
                        value.proposedLowerSupportMax(),
                        BigDecimal.class);
        query =
                bindNullable(
                        query,
                        "proposedUpperMin",
                        value.proposedUpperResistanceMin(),
                        BigDecimal.class);
        query =
                bindNullable(
                        query,
                        "proposedUpperMax",
                        value.proposedUpperResistanceMax(),
                        BigDecimal.class);
        query = bindNullable(query, "lowerMin", value.finalLowerSupportMin(), BigDecimal.class);
        query = bindNullable(query, "lowerMax", value.finalLowerSupportMax(), BigDecimal.class);
        query = bindNullable(query, "upperMin", value.finalUpperResistanceMin(), BigDecimal.class);
        query = bindNullable(query, "upperMax", value.finalUpperResistanceMax(), BigDecimal.class);
        query = bindNullable(query, "zoneDecision", value.zoneDecision(), String.class);
        query = bindNullable(query, "note", value.note(), String.class);
        query = bindNullable(query, "confidence", value.confidence(), Integer.class);
        query = bindNullable(query, "boundaryDecision", value.boundaryDecision(), String.class);
        query = bindNullable(query, "labelCode", value.labelCode(), String.class);
        query = bindNullable(query, "reasonCodes", value.reasonCodes(), String.class);
        query = bindNullable(query, "comment", value.comment(), String.class);
        return query.fetch()
                .rowsUpdated()
                .flatMap(
                        updated ->
                                updated == 0
                                        ? Mono.error(
                                                new IllegalStateException(
                                                        "formation revision conflict"))
                                        : findFormation(value.itemId(), value.reviewerId()));
    }

    public Mono<BoxFormationEvaluation> findFormation(long itemId, String reviewerId) {
        return database.sql(
                        "SELECT * FROM box_formation_evaluation WHERE item_id=:item AND reviewer_id=:reviewer")
                .bind("item", itemId)
                .bind("reviewer", reviewerId)
                .map((row, metadata) -> formation(row))
                .one();
    }

    public Mono<BoxResearchDataset> createDataset(BoxResearchDataset dataset) {
        DatabaseClient.GenericExecuteSpec datasetQuery =
                database.sql(
                                """
                INSERT INTO box_research_dataset(dataset_key, dataset_type, source_batch_id,
                    sampling_policy_json, blind_policy_version, feature_snapshot_version)
                VALUES (:key, :type, :batch, :sampling, :blind, :features)
                """)
                        .bind("key", dataset.datasetKey())
                        .bind("type", dataset.datasetType())
                        .bind("sampling", dataset.samplingPolicyJson())
                        .bind("blind", dataset.blindPolicyVersion())
                        .bind("features", dataset.featureSnapshotVersion());
        datasetQuery =
                dataset.sourceBatchId() == null
                        ? datasetQuery.bindNull("batch", Long.class)
                        : datasetQuery.bind("batch", dataset.sourceBatchId());
        return datasetQuery.fetch().rowsUpdated().then(findDataset(dataset.datasetKey()));
    }

    private Mono<BoxResearchDataset> findDataset(String key) {
        return database.sql("SELECT * FROM box_research_dataset WHERE dataset_key=:key")
                .bind("key", key)
                .map((row, metadata) -> dataset(row))
                .one();
    }

    public Flux<BoxResearchDataset> findDatasets() {
        return database.sql("SELECT * FROM box_research_dataset ORDER BY created_at DESC")
                .map((row, metadata) -> dataset(row))
                .all();
    }

    private BoxResearchDataset dataset(io.r2dbc.spi.Row row) {
        return new BoxResearchDataset(
                number(row.get("id")),
                row.get("dataset_key", String.class),
                row.get("dataset_type", String.class),
                nullableNumber(row.get("source_batch_id")),
                row.get("sampling_policy_json", String.class),
                row.get("blind_policy_version", String.class),
                row.get("feature_snapshot_version", String.class),
                instant(row.get("created_at")));
    }

    private BoxFormationEvaluation formation(io.r2dbc.spi.Row row) {
        return new BoxFormationEvaluation(
                number(row.get("id")),
                number(row.get("item_id")),
                row.get("reviewer_id", String.class),
                FormationLabel.valueOf(row.get("formation_label", String.class)),
                row.get("proposed_start_date", LocalDate.class),
                row.get("proposed_end_date", LocalDate.class),
                row.get("final_start_date", LocalDate.class),
                row.get("final_end_date", LocalDate.class),
                row.get("period_decision", String.class),
                row.get("proposed_lower_support_min", BigDecimal.class),
                row.get("proposed_lower_support_max", BigDecimal.class),
                row.get("proposed_upper_resistance_min", BigDecimal.class),
                row.get("proposed_upper_resistance_max", BigDecimal.class),
                row.get("final_lower_support_min", BigDecimal.class),
                row.get("final_lower_support_max", BigDecimal.class),
                row.get("final_upper_resistance_min", BigDecimal.class),
                row.get("final_upper_resistance_max", BigDecimal.class),
                row.get("zone_decision", String.class),
                row.get("note", String.class),
                row.get("confidence", Integer.class),
                row.get("boundary_decision", String.class),
                row.get("label_code", String.class),
                row.get("reason_codes", String.class),
                row.get("comment_text", String.class),
                number(row.get("revision")),
                instant(row.get("committed_at")));
    }

    private BoxEvaluationCandidate candidateWithId(BoxEvaluationCandidate source, long id) {
        return new BoxEvaluationCandidate(
                id,
                source.itemId(),
                source.candidateKey(),
                source.startDate(),
                source.endDate(),
                source.rankNo(),
                source.featureJson(),
                source.generatorVersion());
    }

    private DatabaseClient.GenericExecuteSpec bindNullable(
            DatabaseClient.GenericExecuteSpec query, String name, Object value, Class<?> type) {
        return value == null ? query.bindNull(name, type) : query.bind(name, value);
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }

    private Long nullableNumber(Object value) {
        return value == null ? null : number(value);
    }

    private Integer nullableInteger(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }

    private BoxBoundaryDecision boundaryDecision(String value) {
        return value == null ? null : BoxBoundaryDecision.valueOf(value);
    }

    private Instant instant(Object value) {
        return value instanceof Instant instant
                ? instant
                : value instanceof OffsetDateTime offset
                        ? offset.toInstant()
                        : Instant.parse(value.toString());
    }
}
