package com.example.kiwoom.research.boxevaluation.sampling;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class StratifiedDiscoverySamplerTest {
    @Test
    void isDeterministicAndRotatesAcrossSymbolsAndConfiguredStrata() {
        var sampler = new StratifiedDiscoverySampler();
        var input =
                List.of(
                        new StratifiedDiscoverySampler.Candidate(
                                "A", LocalDate.of(2024, 1, 1), 10, 2, "BULL"),
                        new StratifiedDiscoverySampler.Candidate(
                                "A", LocalDate.of(2024, 2, 1), 30, 8, "BEAR"),
                        new StratifiedDiscoverySampler.Candidate(
                                "B", LocalDate.of(2024, 1, 1), 10, 2, "BULL"),
                        new StratifiedDiscoverySampler.Candidate(
                                "C", LocalDate.of(2024, 3, 1), 30, 8, "SIDEWAYS"));
        var config = new StratifiedDiscoverySampler.Configuration(List.of(15), List.of(5), 4, 7L);
        var first = sampler.sample(input, config);
        assertThat(first).isEqualTo(sampler.sample(input, config));
        assertThat(first)
                .extracting(StratifiedDiscoverySampler.Candidate::symbol)
                .contains("A", "B", "C");
        assertThat(first)
                .extracting(StratifiedDiscoverySampler.Candidate::marketPeriod)
                .contains("BULL", "BEAR", "SIDEWAYS");
    }
}
