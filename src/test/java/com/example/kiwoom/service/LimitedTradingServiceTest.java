package com.example.kiwoom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.kiwoom.dto.PaperRiskStatus;
import com.example.kiwoom.dto.PerformanceSampleRequest;
import com.example.kiwoom.dto.TradingPerformanceStatus;
import com.example.kiwoom.repository.LimitedTradingRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class LimitedTradingServiceTest {
    private final LimitedTradingRepository repository = mock(LimitedTradingRepository.class);
    private final PaperOrderService orders = mock(PaperOrderService.class);
    private final PaperRiskService risks = mock(PaperRiskService.class);
    private final LimitedTradingService service =
            new LimitedTradingService(repository, orders, risks);

    @Test
    void activatesKillSwitchWhenFiveSamplePerformanceIsNegative() {
        var request =
                new PerformanceSampleRequest(
                        null,
                        "005930",
                        new BigDecimal("10000"),
                        new BigDecimal("10100"),
                        new BigDecimal("-0.01"));
        var degraded =
                new TradingPerformanceStatus(
                        5,
                        new BigDecimal("0.01"),
                        new BigDecimal("-0.01"),
                        new BigDecimal("0.01"),
                        false,
                        null,
                        Instant.now());
        when(repository.addPerformance(request, new BigDecimal("0.01000000")))
                .thenReturn(Mono.empty());
        when(repository.performance()).thenReturn(Mono.just(degraded));
        when(risks.activateAutomatically(contains("비용 후 평균 수익률 음수")))
                .thenReturn(Mono.just(risk(true, "PERFORMANCE_DEGRADATION")));

        var result = service.recordPerformance(request).block();

        assertThat(result.halted()).isTrue();
        verify(risks).activateAutomatically(contains("비용 후 평균 수익률 음수"));
    }

    private PaperRiskStatus risk(boolean halted, String reason) {
        return new PaperRiskStatus(
                BigDecimal.TEN,
                BigDecimal.TEN,
                BigDecimal.ZERO,
                0,
                0,
                0,
                new BigDecimal("0.1"),
                new BigDecimal("0.5"),
                new BigDecimal("0.02"),
                new BigDecimal("0.1"),
                1,
                halted,
                reason,
                Instant.now());
    }
}
