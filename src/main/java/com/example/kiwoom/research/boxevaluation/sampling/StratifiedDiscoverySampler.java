package com.example.kiwoom.research.boxevaluation.sampling;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Research-only sampler. Bucket definitions are configuration, never production thresholds. */
public final class StratifiedDiscoverySampler {
    public record Candidate(
            String symbol, LocalDate cutoffDate, int duration, int width, String marketPeriod) {}

    public record Configuration(
            List<Integer> durationBuckets, List<Integer> widthBuckets, int limit, long seed) {
        public Configuration {
            durationBuckets = List.copyOf(durationBuckets);
            widthBuckets = List.copyOf(widthBuckets);
            if (limit < 0) throw new IllegalArgumentException("limit must be non-negative");
        }
    }

    public List<Candidate> sample(List<Candidate> candidates, Configuration configuration) {
        Map<String, List<Candidate>> strata = new LinkedHashMap<>();
        candidates.stream()
                .sorted(
                        Comparator.comparing(Candidate::symbol)
                                .thenComparing(Candidate::cutoffDate))
                .forEach(
                        candidate ->
                                strata.computeIfAbsent(
                                                stratum(candidate, configuration),
                                                ignored -> new ArrayList<>())
                                        .add(candidate));
        List<Candidate> result = new ArrayList<>();
        int cursor = 0;
        while (result.size() < configuration.limit() && !strata.isEmpty()) {
            List<String> keys = new ArrayList<>(strata.keySet());
            String key = keys.get(cursor++ % keys.size());
            List<Candidate> bucket = strata.get(key);
            if (!bucket.isEmpty()) result.add(bucket.remove(0));
            else strata.remove(key);
        }
        return List.copyOf(result);
    }

    private String stratum(Candidate candidate, Configuration configuration) {
        return bucket(candidate.duration(), configuration.durationBuckets())
                + ":"
                + bucket(candidate.width(), configuration.widthBuckets())
                + ":"
                + candidate.symbol()
                + ":"
                + candidate.marketPeriod();
    }

    private int bucket(int value, List<Integer> boundaries) {
        for (int i = 0; i < boundaries.size(); i++) if (value <= boundaries.get(i)) return i;
        return boundaries.size();
    }
}
