package com.example.kiwoom.service.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.kiwoom.error.ResourceNotFoundException;
import java.util.List;
import org.junit.jupiter.api.Test;

class StrategyRegistryTest {
    @Test
    void resolvesVersionedStrategyAndRejectsUnknownVersion() {
        var registry = new StrategyRegistry(List.of(new DropBaseBreakoutPullbackStrategy()));
        assertThat(registry.require(DropBaseBreakoutPullbackStrategy.VERSION_KEY)).isNotNull();
        assertThatThrownBy(() -> registry.require("unknown-v1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
