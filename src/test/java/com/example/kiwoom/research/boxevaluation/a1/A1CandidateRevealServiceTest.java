package com.example.kiwoom.research.boxevaluation.a1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.kiwoom.research.boxevaluation.model.BoxEvaluationCandidate;
import com.example.kiwoom.research.boxevaluation.model.BoxFormationEvaluation;
import com.example.kiwoom.research.boxevaluation.model.FormationLabel;
import com.example.kiwoom.research.boxevaluation.repository.BoxEvaluationRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class A1CandidateRevealServiceTest {
    @Test
    void narrowsAreRevealedOnlyAfterBoxDecision() {
        BoxEvaluationRepository repository = mock(BoxEvaluationRepository.class);
        when(repository.findFormation(1L, "reviewer"))
                .thenReturn(Mono.just(formation(FormationLabel.BOX)));
        when(repository.findCandidates(1L))
                .thenReturn(
                        Flux.just(
                                new BoxEvaluationCandidate(
                                        1L,
                                        1L,
                                        "NARROW",
                                        LocalDate.of(2020, 1, 1),
                                        LocalDate.of(2020, 2, 1),
                                        1,
                                        "{}",
                                        "v1"),
                                new BoxEvaluationCandidate(
                                        2L,
                                        1L,
                                        "OTHER",
                                        LocalDate.of(2020, 1, 1),
                                        LocalDate.of(2020, 2, 1),
                                        2,
                                        "{}",
                                        "v1")));
        assertThat(new A1CandidateRevealService(repository).revealAfterBox(1L, "reviewer").block())
                .extracting(BoxEvaluationCandidate::candidateKey)
                .containsExactly("NARROW");
    }

    @Test
    void rejectsRevealBeforeBoxDecision() {
        BoxEvaluationRepository repository = mock(BoxEvaluationRepository.class);
        when(repository.findFormation(1L, "reviewer"))
                .thenReturn(Mono.just(formation(FormationLabel.NOT_BOX)));
        assertThatThrownBy(
                        () ->
                                new A1CandidateRevealService(repository)
                                        .revealAfterBox(1L, "reviewer")
                                        .block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("NARROW is available only after BOX");
    }

    private BoxFormationEvaluation formation(FormationLabel label) {
        return new BoxFormationEvaluation(
                1L,
                1L,
                "reviewer",
                label,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                4,
                null,
                null,
                null,
                null,
                1L,
                null);
    }
}
