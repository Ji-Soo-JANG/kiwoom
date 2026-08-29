package com.example.kiwoom.research.boxevaluation.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.kiwoom.dto.StockSearchResult;
import com.example.kiwoom.repository.MarketDataRepository;
import com.example.kiwoom.research.boxevaluation.model.BoxBoundaryDecision;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluation;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluationBatch;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluationBatchStatus;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluationCandidate;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluationDraft;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluationItem;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluationItemStatus;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluationReveal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Flux;

@SpringBootTest(
        properties = {
            "kiwoom.api.base-url=http://localhost",
            "kiwoom.api.key=test-key",
            "kiwoom.api.secret=test-secret",
            "spring.r2dbc.url=r2dbc:h2:mem:///box-evaluation-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.url=jdbc:h2:mem:box-evaluation-flyway;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.username=sa",
            "spring.sql.init.mode=always",
            "spring.flyway.enabled=false"
        })
class BoxEvaluationRepositoryTest {
    @Autowired private BoxEvaluationRepository repository;
    @Autowired private MarketDataRepository marketData;
    @Autowired private DatabaseClient database;

    private long strategyVersionId;

    @BeforeEach
    void prepareReferences() {
        marketData.saveStocks(Flux.just(new StockSearchResult("123456", "평가종목", "KOSPI"))).block();
        strategyVersionId =
                database.sql(
                                """
                        SELECT id FROM strategy_definition
                        WHERE strategy_id='box-evaluation-test' AND version=1
                        """)
                        .map(row -> ((Number) row.get("id")).longValue())
                        .one()
                        .switchIfEmpty(
                                database.sql(
                                                """
                                INSERT INTO strategy_definition(strategy_id, version, name,
                                    description, status, parameters_json)
                                VALUES ('box-evaluation-test', 1, '평가 전략', '저장소 테스트',
                                    'DRAFT', '{}')
                                """)
                                        .filter(statement -> statement.returnGeneratedValues("id"))
                                        .map(row -> ((Number) row.get("id")).longValue())
                                        .one())
                        .block();
    }

    @Test
    void evaluationLifecyclePreservesCutoffAndCommitIdempotency() {
        BoxEvaluationBatch batch = createBatch("수명주기-" + System.nanoTime());
        BoxEvaluationItem item = createItem(batch.id(), 1, LocalDate.of(2024, 6, 28));

        BoxEvaluationCandidate candidate =
                repository
                        .addCandidate(
                                new BoxEvaluationCandidate(
                                        null,
                                        item.id(),
                                        "C1",
                                        LocalDate.of(2023, 1, 10),
                                        LocalDate.of(2024, 4, 5),
                                        1,
                                        "{\"rangeRate\":12.4}",
                                        "generator-v1"))
                        .block();
        assertThat(candidate).isNotNull();

        BoxEvaluationDraft savedDraft =
                repository
                        .saveDraft(
                                new BoxEvaluationDraft(
                                        null,
                                        item.id(),
                                        "reviewer",
                                        BoxBoundaryDecision.CANDIDATE,
                                        "C1",
                                        LocalDate.of(2023, 1, 10),
                                        LocalDate.of(2024, 4, 5),
                                        "VALID_BOX",
                                        4,
                                        "STABLE_RANGE,VOLUME_SPIKES",
                                        "명확한 안정 구간",
                                        0,
                                        null),
                                0)
                        .block();
        assertThat(savedDraft).isNotNull();
        assertThat(savedDraft.draftRevision()).isEqualTo(1);

        BoxEvaluation request =
                new BoxEvaluation(
                        null,
                        item.id(),
                        "reviewer",
                        "commit-" + item.id(),
                        BoxBoundaryDecision.CANDIDATE,
                        "C1",
                        LocalDate.of(2023, 1, 10),
                        LocalDate.of(2024, 4, 5),
                        "VALID_BOX",
                        4,
                        "STABLE_RANGE,VOLUME_SPIKES",
                        "명확한 안정 구간",
                        "{\"cutoffDate\":\"2024-06-28\"}",
                        "box-label-v1",
                        null);
        BoxEvaluation committed = repository.commit(request).block();
        BoxEvaluation duplicate = repository.commit(request).block();

        assertThat(committed).isNotNull();
        assertThat(duplicate.id()).isEqualTo(committed.id());
        assertThat(repository.findItem(item.id()).block().status())
                .isEqualTo(BoxEvaluationItemStatus.COMMITTED);

        BoxEvaluationReveal reveal =
                repository
                        .reveal(
                                new BoxEvaluationReveal(
                                        null,
                                        committed.id(),
                                        "outcome-v1",
                                        "reviewer",
                                        "{\"return20d\":0.12}",
                                        null))
                        .block();
        assertThat(reveal).isNotNull();
        assertThat(repository.findItem(item.id()).block().status())
                .isEqualTo(BoxEvaluationItemStatus.REVEALED);
    }

    @Test
    void candidateAndDraftCannotCrossCutoff() {
        BoxEvaluationBatch batch = createBatch("cutoff-" + System.nanoTime());
        BoxEvaluationItem item = createItem(batch.id(), 1, LocalDate.of(2024, 6, 28));

        assertThatThrownBy(
                        () ->
                                repository
                                        .addCandidate(
                                                new BoxEvaluationCandidate(
                                                        null,
                                                        item.id(),
                                                        "FUTURE",
                                                        LocalDate.of(2024, 1, 1),
                                                        LocalDate.of(2024, 7, 1),
                                                        1,
                                                        "{}",
                                                        "generator-v1"))
                                        .block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cutoff");

        assertThatThrownBy(
                        () ->
                                repository
                                        .saveDraft(
                                                new BoxEvaluationDraft(
                                                        null,
                                                        item.id(),
                                                        "reviewer",
                                                        BoxBoundaryDecision.MANUAL,
                                                        null,
                                                        LocalDate.of(2024, 1, 1),
                                                        LocalDate.of(2024, 7, 1),
                                                        "VALID_BOX",
                                                        3,
                                                        "STABLE_RANGE",
                                                        null,
                                                        0,
                                                        null),
                                                0)
                                        .block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cutoff");
    }

    private BoxEvaluationBatch createBatch(String name) {
        return repository
                .createBatch(
                        new BoxEvaluationBatch(
                                null,
                                strategyVersionId,
                                name,
                                "dataset-v1",
                                "generator-v1",
                                "{\"sample\":10}",
                                "blind-v1",
                                BoxEvaluationBatchStatus.DRAFT,
                                "test",
                                null))
                .block();
    }

    private BoxEvaluationItem createItem(long batchId, int order, LocalDate cutoff) {
        return repository
                .createItem(
                        new BoxEvaluationItem(
                                null,
                                batchId,
                                "123456",
                                cutoff,
                                order,
                                null,
                                "sha256:test",
                                BoxEvaluationItemStatus.PENDING,
                                0,
                                null))
                .block();
    }
}
