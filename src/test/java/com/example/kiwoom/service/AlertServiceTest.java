package com.example.kiwoom.service;

import com.example.kiwoom.dto.*;
import com.example.kiwoom.repository.AlertRepository;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class AlertServiceTest {
    private final AlertRepository repository = mock(AlertRepository.class);
    private final KiwoomApiService kiwoomApiService = mock(KiwoomApiService.class);
    private final AlertService service = new AlertService(repository, kiwoomApiService);

    @Test
    void createsEventOnlyWhenRuleCrossesIntoMatchedState() {
        AlertRule rule = new AlertRule(1L, "005930", AlertConditionType.PRICE_ABOVE,
                new BigDecimal("80000"), true, false);
        AlertEvent event = new AlertEvent(10L, 1L, "005930", AlertConditionType.PRICE_ABOVE,
                new BigDecimal("81000"), new BigDecimal("80000"), OffsetDateTime.now(), null);
        when(repository.findEnabledRules("alice")).thenReturn(Flux.just(rule));
        when(kiwoomApiService.getStockCurrentPrice("005930"))
                .thenReturn(Mono.just(new StockPriceResponse("005930", "81000", "0", "0")));
        when(repository.transitionToTriggered("alice", 1L)).thenReturn(Mono.just(true));
        when(repository.addEvent("alice", rule, new BigDecimal("81000"))).thenReturn(Mono.just(event));

        assertEquals(List.of(event), service.evaluate("alice").collectList().block());
    }

    @Test
    void doesNotDuplicateEventWhileConditionRemainsMatched() {
        AlertRule rule = new AlertRule(1L, "005930", AlertConditionType.PRICE_ABOVE,
                new BigDecimal("80000"), true, true);
        when(repository.findEnabledRules("alice")).thenReturn(Flux.just(rule));
        when(kiwoomApiService.getStockCurrentPrice("005930"))
                .thenReturn(Mono.just(new StockPriceResponse("005930", "81000", "0", "0")));
        when(repository.transitionToTriggered("alice", 1L)).thenReturn(Mono.just(false));

        assertTrue(service.evaluate("alice").collectList().block().isEmpty());
        verify(repository, never()).addEvent(anyString(), any(), any());
    }

    @Test
    void evaluatesRsiRuleFromBackendDailyIndicators() {
        AlertRule rule = new AlertRule(2L, "005930", AlertConditionType.RSI_BELOW,
                new BigDecimal("30"), true, false);
        DailyPriceResponse latest = new DailyPriceResponse("20260816", 100, 100, 100, 100, 1);
        latest.setIndicators(25d, 1d, 2d);
        AlertEvent event = new AlertEvent(11L, 2L, "005930", AlertConditionType.RSI_BELOW,
                new BigDecimal("25.0"), new BigDecimal("30"), OffsetDateTime.now(), null);
        when(repository.findEnabledRules("alice")).thenReturn(Flux.just(rule));
        when(kiwoomApiService.getDailyPrices("005930", null)).thenReturn(Mono.just(List.of(latest)));
        when(repository.transitionToTriggered("alice", 2L)).thenReturn(Mono.just(true));
        when(repository.addEvent("alice", rule, new BigDecimal("25.0"))).thenReturn(Mono.just(event));

        assertEquals(List.of(event), service.evaluate("alice").collectList().block());
    }

    @Test
    void evaluatesDailyDropRateAgainstPositiveThreshold() {
        AlertRule rule = new AlertRule(3L, "005930", AlertConditionType.CHANGE_RATE_BELOW,
                new BigDecimal("5"), true, false);
        DailyPriceResponse previous = new DailyPriceResponse("20260815", 100, 100, 100, 100, 1);
        DailyPriceResponse latest = new DailyPriceResponse("20260816", 94, 95, 93, 94, 2);
        AlertEvent event = new AlertEvent(12L, 3L, "005930", AlertConditionType.CHANGE_RATE_BELOW,
                new BigDecimal("-6.0"), new BigDecimal("5"), OffsetDateTime.now(), null);
        when(repository.findEnabledRules("alice")).thenReturn(Flux.just(rule));
        when(kiwoomApiService.getDailyPrices("005930", null)).thenReturn(Mono.just(List.of(previous, latest)));
        when(repository.transitionToTriggered("alice", 3L)).thenReturn(Mono.just(true));
        when(repository.addEvent("alice", rule, new BigDecimal("-6.0"))).thenReturn(Mono.just(event));

        assertEquals(List.of(event), service.evaluate("alice").collectList().block());
    }
}
