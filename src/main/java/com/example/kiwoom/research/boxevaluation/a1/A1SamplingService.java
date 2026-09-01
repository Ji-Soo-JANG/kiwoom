package com.example.kiwoom.research.boxevaluation.a1;

import java.util.List;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class A1SamplingService {
    private final A1EligibilityRepository eligibility;
    private final A1Sampler sampler;

    public A1SamplingService(A1EligibilityRepository eligibility) {
        this.eligibility = eligibility;
        this.sampler = new A1Sampler();
    }

    public Mono<List<A1Sample>> preview() {
        return eligibility
                .findEligibleSymbols()
                .map(sampler::sample)
                .flatMap(samples -> eligibility.enrichSamples(samples, false));
    }

    public Mono<List<A1Sample>> refreshEligibilityEvidence(List<A1Sample> samples) {
        return eligibility.enrichSamples(samples, true);
    }
}
