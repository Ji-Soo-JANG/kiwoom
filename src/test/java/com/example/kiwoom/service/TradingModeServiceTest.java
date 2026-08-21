package com.example.kiwoom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.kiwoom.config.TradingProperties;
import com.example.kiwoom.dto.TradingMode;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TradingModeServiceTest {
    @Test
    void signalOnlyRejectsOrderCreation() {
        TradingModeService service =
                new TradingModeService(properties(TradingMode.SIGNAL_ONLY, false, ""));

        assertThat(service.status().effectiveMode()).isEqualTo(TradingMode.SIGNAL_ONLY);
        assertThatThrownBy(service::requireOrderCreationMode)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SIGNAL_ONLY");
    }

    @Test
    void paperModeAllowsOnlyLocalOrderCreation() {
        TradingModeService service =
                new TradingModeService(properties(TradingMode.PAPER, false, ""));

        assertThat(service.requireOrderCreationMode()).isEqualTo(TradingMode.PAPER);
        assertThat(service.status().externalOrderSubmissionAvailable()).isFalse();
    }

    @Test
    void liveModeFallsBackToSignalOnlyEvenWhenEnvironmentLocksAreArmed() {
        TradingModeService service =
                new TradingModeService(
                        properties(TradingMode.LIVE, true, TradingModeService.LIVE_CONFIRMATION));

        assertThat(service.status().liveArmed()).isTrue();
        assertThat(service.status().effectiveMode()).isEqualTo(TradingMode.SIGNAL_ONLY);
        assertThat(service.status().blockers()).contains("실주문 브로커 어댑터가 아직 연결되지 않았습니다.");
    }

    private TradingProperties properties(
            TradingMode mode, boolean liveEnabled, String confirmation) {
        return new TradingProperties(
                mode,
                liveEnabled,
                confirmation,
                BigDecimal.valueOf(10_000_000),
                BigDecimal.valueOf(0.1),
                BigDecimal.valueOf(0.5),
                BigDecimal.valueOf(0.02),
                BigDecimal.valueOf(0.1),
                5);
    }
}
