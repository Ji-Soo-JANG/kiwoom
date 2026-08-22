package com.example.kiwoom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.kiwoom.dto.LimitedTradeCandidate;
import com.example.kiwoom.dto.StrategyCandidate;
import com.example.kiwoom.dto.StrategyScanResponse;
import com.example.kiwoom.dto.TradeCandidateRequest;
import com.example.kiwoom.repository.MarketDataRepository;
import com.example.kiwoom.repository.StrategySnapshotRepository;
import com.example.kiwoom.service.strategy.DropBaseBreakoutPullbackStrategy;
import com.example.kiwoom.service.strategy.StrategyRegistry;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

class StrategyScanPipelineTest {
    @Test
    void qualifiedAffordableCandidateIsRegisteredForApproval() {
        LimitedTradingService limited = mock(LimitedTradingService.class);
        when(limited.create(any()))
                .thenReturn(
                        Mono.just(
                                new LimitedTradeCandidate(
                                        1,
                                        "signal",
                                        "005930",
                                        "reason",
                                        java.math.BigDecimal.TEN,
                                        1,
                                        "PENDING",
                                        Instant.now(),
                                        null,
                                        null,
                                        null,
                                        Instant.now(),
                                        Instant.now())));
        StrategyScanService service =
                new StrategyScanService(
                        mock(MarketDataRepository.class),
                        mock(StrategySnapshotRepository.class),
                        limited,
                        new StrategyRegistry(List.of(new DropBaseBreakoutPullbackStrategy())));
        var candidate =
                new StrategyCandidate(
                        "005930",
                        "삼성전자",
                        70_000,
                        90,
                        true,
                        -0.3,
                        0.1,
                        3,
                        0.05,
                        -0.02,
                        List.of("급락", "횡보", "눌림목"));
        var response =
                new StrategyScanResponse(
                        12,
                        "v1",
                        60,
                        List.of(candidate),
                        1,
                        "test",
                        LocalDate.now(),
                        Instant.now());

        assertThat(service.registerCandidates(response).block()).isSameAs(response);
        ArgumentCaptor<TradeCandidateRequest> request =
                ArgumentCaptor.forClass(TradeCandidateRequest.class);
        verify(limited).create(request.capture());
        assertThat(request.getValue().signalId()).isEqualTo("scan-12-005930");
        assertThat(request.getValue().suggestedQuantity()).isEqualTo(1);
    }
}
