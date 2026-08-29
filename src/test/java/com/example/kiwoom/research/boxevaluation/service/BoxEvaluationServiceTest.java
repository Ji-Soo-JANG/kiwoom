package com.example.kiwoom.research.boxevaluation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.kiwoom.research.boxevaluation.dto.CommitBoxEvaluationRequest;
import com.example.kiwoom.research.boxevaluation.model.BoxBoundaryDecision;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluation;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluationCandidate;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluationItem;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluationItemStatus;
import com.example.kiwoom.research.boxevaluation.repository.BoxEvaluationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class BoxEvaluationServiceTest {
    private BoxEvaluationRepository repository;
    private BoxEvaluationService service;
    private BoxEvaluationItem item;

    @BeforeEach
    void setUp() {
        repository = mock(BoxEvaluationRepository.class);
        service = new BoxEvaluationService(repository, new ObjectMapper().findAndRegisterModules());
        item =
                new BoxEvaluationItem(
                        10L,
                        20L,
                        "123456",
                        LocalDate.of(2026, 8, 21),
                        1,
                        null,
                        "hash",
                        BoxEvaluationItemStatus.PENDING,
                        0,
                        null);
        when(repository.findItem(10L)).thenReturn(Mono.just(item));
    }

    @Test
    void positiveLabelRequiresBoundary() {
        CommitBoxEvaluationRequest request =
                request(BoxBoundaryDecision.NO_SUITABLE_CANDIDATE, null, null, null, "VALID_BOX");

        assertThatThrownBy(() -> service.commit(10L, request).block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("경계를 지정");
    }

    @Test
    void negativeLabelRequiresNoSuitableCandidateDecision() {
        CommitBoxEvaluationRequest request =
                request(
                        BoxBoundaryDecision.MANUAL,
                        null,
                        LocalDate.of(2026, 1, 2),
                        LocalDate.of(2026, 7, 31),
                        "NOT_BOX");

        assertThatThrownBy(() -> service.commit(10L, request).block())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("적합 후보 없음");
    }

    @Test
    void candidateMustMatchStoredBoundaryAndClosesCompletedBatch() {
        LocalDate start = LocalDate.of(2026, 1, 2);
        LocalDate end = LocalDate.of(2026, 7, 31);
        when(repository.findCandidates(10L))
                .thenReturn(
                        Flux.just(
                                new BoxEvaluationCandidate(
                                        1L, 10L, "C1", start, end, 1, "{}", "generator-v1")));
        when(repository.commit(any(BoxEvaluation.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(repository.closeBatchIfComplete(20L)).thenReturn(Mono.just(1L));

        BoxEvaluation saved =
                service.commit(
                                10L,
                                request(
                                        BoxBoundaryDecision.CANDIDATE,
                                        "C1",
                                        start,
                                        end,
                                        "VALID_BOX"))
                        .block();

        assertThat(saved.boundaryDecision()).isEqualTo(BoxBoundaryDecision.CANDIDATE);
        assertThat(saved.evaluationSchemaVersion()).isEqualTo("box-label-v2");
        verify(repository).closeBatchIfComplete(20L);
    }

    private CommitBoxEvaluationRequest request(
            BoxBoundaryDecision decision,
            String candidate,
            LocalDate start,
            LocalDate end,
            String label) {
        return new CommitBoxEvaluationRequest(
                "reviewer",
                "commit-key",
                decision,
                candidate,
                start,
                end,
                label,
                4,
                "STABLE_RANGE",
                "설명");
    }
}
