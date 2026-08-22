package com.example.kiwoom.service.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.kiwoom.error.ResourceNotFoundException;
import java.util.List;
import org.junit.jupiter.api.Test;

class StrategyRegistryTest {
    @Test
    void resolvesVersionedStrategyAndRejectsUnknownVersion() {
        var registry =
                new StrategyRegistry(
                        List.of(
                                new DropBaseBreakoutPullbackStrategy(),
                                new MultiPeriodRecoveryPullbackStrategy()));
        assertThat(registry.require(DropBaseBreakoutPullbackStrategy.VERSION_KEY)).isNotNull();
        assertThat(registry.require(MultiPeriodRecoveryPullbackStrategy.VERSION_KEY)).isNotNull();
        assertThat(
                        registry.require(MultiPeriodRecoveryPullbackStrategy.VERSION_KEY)
                                .requiredHistoryDays())
                .isEqualTo(1500);
        assertThatThrownBy(() -> registry.require("unknown-v1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
