package com.example.kiwoom.research.boxevaluation.a1;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Deterministic, symbol-first sampler for TASK-004 Discovery A1. */
public final class A1Sampler {
    public static final long SEED = 20260901L;
    public static final int MARKET_QUOTA = 10;
    public static final int CONTEXT_CANDLES = 252;
    public static final String ALGORITHM_VERSION = "a1-symbol-first-cutoff-second-v1";

    public List<A1Sample> sample(List<A1EligibleSymbol> input) {
        if (input == null) throw new IllegalArgumentException("eligible symbols are required");
        Map<String, List<A1EligibleSymbol>> byMarket =
                input.stream().collect(Collectors.groupingBy(A1EligibleSymbol::market));
        List<A1EligibleSymbol> kospi = select(byMarket.getOrDefault("KOSPI", List.of()), "KOSPI");
        List<A1EligibleSymbol> kosdaq =
                select(byMarket.getOrDefault("KOSDAQ", List.of()), "KOSDAQ");
        List<A1EligibleSymbol> selected = new ArrayList<>(20);
        selected.addAll(kospi);
        selected.addAll(kosdaq);
        if (selected.stream().map(A1EligibleSymbol::code).distinct().count() != 20) {
            throw new A1SamplingException("selected symbols are not unique");
        }
        Map<LocalDate, A1TimeStratum> dateStrata = dateStrata(input);
        List<A1TimeStratum> assignments = assignments();
        List<A1Sample> result = new ArrayList<>();
        for (int i = 0; i < selected.size(); i++) {
            A1EligibleSymbol symbol = selected.get(i);
            A1TimeStratum assigned = assignments.get(i);
            A1Sample sample = choose(symbol, assigned, dateStrata);
            result.add(sample);
        }
        return List.copyOf(result);
    }

    private List<A1EligibleSymbol> select(List<A1EligibleSymbol> candidates, String market) {
        if (candidates.size() < MARKET_QUOTA) {
            throw new A1SamplingException(market + " eligible quota is unavailable");
        }
        return candidates.stream()
                .sorted(
                        Comparator.comparingLong((A1EligibleSymbol s) -> rank(s.code(), market))
                                .thenComparing(A1EligibleSymbol::code))
                .limit(MARKET_QUOTA)
                .toList();
    }

    private long rank(String code, String market) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes =
                    digest.digest(
                            (SEED + ":" + market + ":" + code).getBytes(StandardCharsets.UTF_8));
            long value = 0;
            for (int i = 0; i < Long.BYTES; i++) value = (value << 8) | (bytes[i] & 0xffL);
            return value ^ Long.MIN_VALUE;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private Map<LocalDate, A1TimeStratum> dateStrata(List<A1EligibleSymbol> eligiblePopulation) {
        List<LocalDate> dates =
                eligiblePopulation.stream()
                        .flatMap(s -> s.eligibleCutoffDates().stream())
                        .distinct()
                        .sorted()
                        .toList();
        if (dates.isEmpty()) throw new A1SamplingException("no eligible cutoff dates");
        Map<LocalDate, A1TimeStratum> result = new HashMap<>();
        for (int i = 0; i < dates.size(); i++) {
            int bucket = (i * 3) / dates.size();
            result.put(
                    dates.get(i),
                    bucket == 0
                            ? A1TimeStratum.EARLY
                            : bucket == 1 ? A1TimeStratum.MIDDLE : A1TimeStratum.RECENT);
        }
        return result;
    }

    private List<A1TimeStratum> assignments() {
        List<A1TimeStratum> result = new ArrayList<>();
        result.addAll(java.util.Collections.nCopies(7, A1TimeStratum.EARLY));
        result.addAll(java.util.Collections.nCopies(6, A1TimeStratum.MIDDLE));
        result.addAll(java.util.Collections.nCopies(7, A1TimeStratum.RECENT));
        return result;
    }

    private A1Sample choose(
            A1EligibleSymbol symbol,
            A1TimeStratum assigned,
            Map<LocalDate, A1TimeStratum> dateStrata) {
        EnumMap<A1TimeStratum, List<LocalDate>> byStratum = new EnumMap<>(A1TimeStratum.class);
        for (A1TimeStratum stratum : A1TimeStratum.values())
            byStratum.put(stratum, new ArrayList<>());
        for (LocalDate date : symbol.eligibleCutoffDates()) {
            A1TimeStratum stratum = dateStrata.get(date);
            if (stratum != null) byStratum.get(stratum).add(date);
        }
        for (List<LocalDate> dates : byStratum.values()) dates.sort(LocalDate::compareTo);
        List<A1TimeStratum> order =
                switch (assigned) {
                    case MIDDLE ->
                            List.of(
                                    A1TimeStratum.MIDDLE,
                                    A1TimeStratum.EARLY,
                                    A1TimeStratum.RECENT);
                    case EARLY ->
                            List.of(
                                    A1TimeStratum.EARLY,
                                    A1TimeStratum.MIDDLE,
                                    A1TimeStratum.RECENT);
                    case RECENT ->
                            List.of(
                                    A1TimeStratum.RECENT,
                                    A1TimeStratum.MIDDLE,
                                    A1TimeStratum.EARLY);
                };
        for (int i = 0; i < order.size(); i++) {
            A1TimeStratum actual = order.get(i);
            List<LocalDate> dates = byStratum.get(actual);
            if (!dates.isEmpty()) {
                LocalDate selected =
                        dates.get(
                                (int)
                                        Math.floorMod(
                                                rank(symbol.code() + ":" + actual, symbol.market()),
                                                dates.size()));
                return new A1Sample(
                        symbol.code(), symbol.market(), selected, assigned, actual, i > 0);
            }
        }
        throw new A1SamplingException("selected symbol has no valid cutoff: " + symbol.code());
    }
}
