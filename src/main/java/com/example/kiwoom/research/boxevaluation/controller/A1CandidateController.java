package com.example.kiwoom.research.boxevaluation.controller;

import com.example.kiwoom.research.boxevaluation.a1.A1CandidateRevealService;
import com.example.kiwoom.research.boxevaluation.model.BoxEvaluationCandidate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/research/box-evaluations/a1")
public class A1CandidateController {
    private final A1CandidateRevealService service;

    public A1CandidateController(A1CandidateRevealService service) {
        this.service = service;
    }

    @GetMapping("/items/{itemId}/narrow")
    public Mono<List<BoxEvaluationCandidate>> narrow(
            @PathVariable long itemId, @RequestParam String reviewerId) {
        return service.revealAfterBox(itemId, reviewerId);
    }
}
