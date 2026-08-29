package com.example.kiwoom.research.boxevaluation.repository;

import com.example.kiwoom.dto.StoredDailyCandle;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluation;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluationBatch;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluationBatchStatus;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluationCandidate;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluationDraft;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluationItem;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluationItemStatus;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluationReveal;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluationSupersede;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
                UPDATE box_evaluation_draft d SET selected_candidate_key=:candidate,
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
                        transitionToCommitted(evaluation.itemId())
                                .flatMap(
                                        changed ->
                                                changed == 1
                                                        ? insertEvaluation(evaluation)
                                                        : Mono.error(
                                                                new IllegalStateException(
                                                                        "확정할 수 없는 평가 항목 상태입니다."))));
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
                INSERT INTO box_evaluation_draft(item_id, reviewer_id, selected_candidate_key,
                    edited_start_date, edited_end_date, label_code, confidence, reason_codes,
                    comment_text, draft_revision)
                SELECT :item, :reviewer, :candidate, :startDate, :endDate, :label, :confidence,
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

    private Mono<BoxEvaluation> insertEvaluation(BoxEvaluation evaluation) {
        DatabaseClient.GenericExecuteSpec query =
                database.sql(
                                """
                INSERT INTO box_evaluation(item_id, reviewer_id, commit_key,
                    selected_candidate_key, final_start_date, final_end_date, label_code,
                    confidence, reason_codes, comment_text, input_snapshot_json,
                    evaluation_schema_version)
                SELECT :item, :reviewer, :commitKey, :candidate, :startDate, :endDate, :label,
                    :confidence, :reasons, :comment, :snapshot, :schema
                FROM box_evaluation_item i WHERE i.id=:item AND i.status='COMMITTED'
                  AND (:startDate IS NULL OR :endDate IS NULL
                       OR (:startDate <= :endDate AND :endDate <= i.cutoff_date))
                """)
                        .bind("item", evaluation.itemId())
                        .bind("reviewer", evaluation.reviewerId())
                        .bind("commitKey", evaluation.commitKey())
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

    private Instant instant(Object value) {
        return value instanceof Instant instant
                ? instant
                : value instanceof OffsetDateTime offset
                        ? offset.toInstant()
                        : Instant.parse(value.toString());
    }
}
