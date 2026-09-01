package com.example.kiwoom.research.boxevaluation.a1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class A1SamplerTest {
    private static final List<LocalDate> ALL_DATES =
            List.of(LocalDate.of(2015, 1, 2), LocalDate.of(2019, 1, 2), LocalDate.of(2024, 1, 2));

    @Test
    void selectsExactlyTenSymbolsPerMarketAndSevenSixSevenStrata() {
        List<A1Sample> result = new A1Sampler().sample(input(ALL_DATES));
        assertThat(result).hasSize(20);
        assertThat(result)
                .extracting(A1Sample::market)
                .containsExactlyElementsOf(
                        concat(
                                Collections.nCopies(10, "KOSPI"),
                                Collections.nCopies(10, "KOSDAQ")));
        assertThat(result).extracting(A1Sample::code).doesNotHaveDuplicates();
        assertThat(result)
                .extracting(A1Sample::assignedStratum)
                .containsExactlyElementsOf(
                        concat(
                                Collections.nCopies(7, A1TimeStratum.EARLY),
                                Collections.nCopies(6, A1TimeStratum.MIDDLE),
                                Collections.nCopies(7, A1TimeStratum.RECENT)));
    }

    @Test
    void isIndependentOfDatabaseInputOrder() {
        List<A1EligibleSymbol> first = input(ALL_DATES);
        List<A1EligibleSymbol> second = new ArrayList<>(first);
        Collections.reverse(second);
        assertThat(new A1Sampler().sample(first)).isEqualTo(new A1Sampler().sample(second));
    }

    @Test
    void choosesOnlyActualEligibleCandleDates() {
        List<A1Sample> result =
                new A1Sampler()
                        .sample(input(List.of(LocalDate.of(2016, 3, 3), LocalDate.of(2021, 7, 7))));
        assertThat(result)
                .allSatisfy(
                        sample ->
                                assertThat(
                                                List.of(
                                                        LocalDate.of(2016, 3, 3),
                                                        LocalDate.of(2021, 7, 7)))
                                        .contains(sample.cutoffDate()));
    }

    @Test
    void appliesAssignedStratumFallbackWithoutChangingSymbol() {
        List<A1EligibleSymbol> input = input(ALL_DATES);
        String target =
                new A1Sampler()
                        .sample(input).stream()
                                .filter(sample -> sample.assignedStratum() != A1TimeStratum.EARLY)
                                .findFirst()
                                .orElseThrow()
                                .code();
        int index = input.stream().map(A1EligibleSymbol::code).toList().indexOf(target);
        input.set(
                index,
                new A1EligibleSymbol(
                        target, input.get(index).market(), List.of(LocalDate.of(2015, 1, 2))));
        A1Sample selected =
                new A1Sampler()
                        .sample(input).stream()
                                .filter(sample -> sample.code().equals(target))
                                .findFirst()
                                .orElseThrow();
        assertThat(selected.code()).isEqualTo(target);
        assertThat(selected.fallbackApplied()).isTrue();
        assertThat(selected.actualStratum()).isEqualTo(A1TimeStratum.EARLY);
    }

    @Test
    void rejectsInsufficientMarketQuota() {
        List<A1EligibleSymbol> input = input(ALL_DATES).subList(0, 19);
        assertThatThrownBy(() -> new A1Sampler().sample(input))
                .isInstanceOf(A1SamplingException.class)
                .hasMessageContaining("KOSDAQ");
    }

    @Test
    void rejectsDuplicateSymbolAcrossMarkets() {
        List<A1EligibleSymbol> input = input(ALL_DATES);
        input.set(10, new A1EligibleSymbol("100000", "KOSDAQ", ALL_DATES));

        assertThatThrownBy(() -> new A1Sampler().sample(input))
                .isInstanceOf(A1SamplingException.class)
                .hasMessageContaining("not unique");
    }

    @Test
    void doesNotUseProductTypeOrVolumeBecauseInputHasNoSuchFields() {
        List<A1Sample> baseline = new A1Sampler().sample(input(ALL_DATES));
        List<A1EligibleSymbol> reordered =
                input(ALL_DATES).stream()
                        .map(
                                symbol ->
                                        new A1EligibleSymbol(
                                                symbol.code(),
                                                symbol.market(),
                                                symbol.eligibleCutoffDates()))
                        .toList();
        assertThat(baseline).isEqualTo(new A1Sampler().sample(reordered));
    }

    private List<A1EligibleSymbol> input(List<LocalDate> dates) {
        List<A1EligibleSymbol> result = new ArrayList<>();
        for (int i = 0; i < 10; i++)
            result.add(new A1EligibleSymbol(String.format("100%03d", i), "KOSPI", dates));
        for (int i = 0; i < 10; i++)
            result.add(new A1EligibleSymbol(String.format("200%03d", i), "KOSDAQ", dates));
        return result;
    }

    @SafeVarargs
    private final <T> List<T> concat(List<T>... parts) {
        List<T> result = new ArrayList<>();
        for (List<T> part : parts) result.addAll(part);
        return result;
    }
}
