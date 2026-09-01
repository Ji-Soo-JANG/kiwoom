package com.example.kiwoom.research.boxevaluation.a1;

import com.example.kiwoom.research.boxevaluation.model.BoxEvaluationCandidate;
import com.example.kiwoom.research.boxevaluation.model.FormationLabel;
import com.example.kiwoom.research.boxevaluation.repository.BoxEvaluationRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class A1CandidateRevealService {
    private final BoxEvaluationRepository repository;

    public A1CandidateRevealService(BoxEvaluationRepository repository) {
        this.repository = repository;
    }

    public Mono<List<BoxEvaluationCandidate>> revealAfterBox(long itemId, String reviewerId) {
        return repository
                .findFormation(itemId, reviewerId)
                .switchIfEmpty(
                        Mono.error(
                                new IllegalStateException(
                                        "initial formation decision is required")))
                .flatMapMany(
                        formation -> {
                            if (formation.formationLabel() != FormationLabel.BOX) {
                                return reactor.core.publisher.Flux.error(
                                        new IllegalStateException(
                                                "NARROW is available only after BOX"));
                            }
                            return repository
                                    .findCandidates(itemId)
                                    .filter(candidate -> "NARROW".equals(candidate.candidateKey()));
                        })
                .collectList();
    }
}
