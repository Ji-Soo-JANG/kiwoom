package com.example.kiwoom.research.boxevaluation.candidate;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.kiwoom.research.boxevaluation.dto.BoxCandidateGenerationResult;
import com.example.kiwoom.research.boxevaluation.model.BoxCandidate;
import com.example.kiwoom.research.boxevaluation.model.BoxCandidateFeatures;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Keeps STR-P06-R2 box-boundary evidence separate from entry, PnL and order decisions. */
class BoxCandidateScopeContractTest {
    private static final List<String> FORBIDDEN_DECISION_TERMS =
            List.of(
                    "return",
                    "profit",
                    "loss",
                    "target",
                    "stop",
                    "entry",
                    "exit",
                    "order",
                    "quantity",
                    "position",
                    "qualified",
                    "buy",
                    "sell");

    @Test
    void blindCandidateContractContainsNoPerformanceRiskBarrierOrOrderDecision() {
        List<Class<? extends Record>> blindContract =
                List.of(
                        BoxCandidateGenerationResult.class,
                        BoxCandidate.class,
                        BoxCandidateFeatures.class);

        List<String> componentNames =
                blindContract.stream()
                        .flatMap(type -> Arrays.stream(type.getRecordComponents()))
                        .map(RecordComponent::getName)
                        .map(name -> name.toLowerCase(Locale.ROOT))
                        .toList();

        assertThat(componentNames)
                .allSatisfy(
                        component ->
                                assertThat(FORBIDDEN_DECISION_TERMS.stream())
                                        .noneMatch(component::contains));
    }

    @Test
    void candidateGeneratorPublicApiOnlyAcceptsCandlesCutoffAndResearchParameters() {
        List<String> publicParameterTypes =
                Arrays.stream(BoxCandidateGenerator.class.getDeclaredMethods())
                        .filter(
                                method ->
                                        java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                        .flatMap(method -> Stream.of(method.getParameterTypes()))
                        .map(Class::getName)
                        .distinct()
                        .sorted()
                        .toList();

        assertThat(publicParameterTypes)
                .containsExactly(
                        "com.example.kiwoom.research.boxevaluation.model.BoxCandidateParameters",
                        "java.time.LocalDate",
                        "java.util.List");
    }
}
