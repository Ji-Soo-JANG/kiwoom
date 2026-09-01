package com.example.kiwoom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.kiwoom.broker.kiwoom.client.DailyChartPage;
import com.example.kiwoom.dto.DailyPriceResponse;
import com.example.kiwoom.repository.HistoricalBackfillRepository;
import com.example.kiwoom.repository.MarketDataRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;

class HistoricalDailyBackfillServiceTest {
    @Test
    void stateAbsentAndZeroCandlesInitializesBeforeBrokerTraversal() {
        var api = Mockito.mock(KiwoomApiService.class);
        var candles = Mockito.mock(MarketDataRepository.class);
        var states = Mockito.mock(HistoricalBackfillRepository.class);
        var target = LocalDate.of(2015, 1, 1);
        var initial =
                new HistoricalBackfillState(
                        "282620",
                        target,
                        null,
                        HistoricalBackfillStatus.IN_PROGRESS,
                        null,
                        null,
                        false,
                        0,
                        0,
                        1,
                        null,
                        null);
        var terminal =
                new HistoricalBackfillState(
                        "282620",
                        target,
                        LocalDate.of(2014, 12, 31),
                        HistoricalBackfillStatus.TARGET_REACHED,
                        null,
                        null,
                        false,
                        1,
                        1,
                        1,
                        null,
                        null);
        when(states.find("282620"))
                .thenReturn(Mono.empty(), Mono.just(initial), Mono.just(terminal));
        when(candles.findOldestCandleDate("282620")).thenReturn(Mono.empty());
        when(states.createPending("282620", target)).thenReturn(Mono.empty());
        when(states.start("282620", target, null)).thenReturn(Mono.empty());
        when(api.getDailyChartPage(eq("282620"), any(LocalDate.class), eq(null)))
                .thenReturn(
                        Mono.just(
                                new DailyChartPage(
                                        List.of(
                                                new DailyPriceResponse(
                                                        "20141231", 10, 12, 9, 11, 100)),
                                        false,
                                        null)));
        when(states.persistPage(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(Boolean.class),
                        any(Integer.class),
                        any(Long.class)))
                .thenReturn(Mono.empty());
        when(states.finish("282620", HistoricalBackfillStatus.TARGET_REACHED, null))
                .thenReturn(Mono.empty());

        var result =
                new HistoricalDailyBackfillService(api, candles, states)
                        .backfill("282620", target)
                        .block();

        assertThat(result).isEqualTo(terminal);
        verify(states).createPending("282620", target);
        verify(states).start("282620", target, null);
        verify(api).getDailyChartPage(eq("282620"), any(LocalDate.class), eq(null));
    }

    @Test
    void stateAbsentBrokerFailureIsPersistedAfterInitialization() {
        var api = Mockito.mock(KiwoomApiService.class);
        var candles = Mockito.mock(MarketDataRepository.class);
        var states = Mockito.mock(HistoricalBackfillRepository.class);
        var target = LocalDate.of(2015, 1, 1);
        var initial =
                new HistoricalBackfillState(
                        "282621",
                        target,
                        null,
                        HistoricalBackfillStatus.IN_PROGRESS,
                        null,
                        null,
                        false,
                        0,
                        0,
                        1,
                        null,
                        null);
        var failed =
                new HistoricalBackfillState(
                        "282621",
                        target,
                        null,
                        HistoricalBackfillStatus.FAILED,
                        null,
                        null,
                        false,
                        0,
                        0,
                        1,
                        "BACKFILL_ERROR",
                        "controlled broker failure");
        when(states.find("282621")).thenReturn(Mono.empty(), Mono.just(initial), Mono.just(failed));
        when(candles.findOldestCandleDate("282621")).thenReturn(Mono.empty());
        when(states.createPending("282621", target)).thenReturn(Mono.empty());
        when(states.start("282621", target, null)).thenReturn(Mono.empty());
        when(api.getDailyChartPage(eq("282621"), any(LocalDate.class), eq(null)))
                .thenReturn(Mono.error(new IllegalStateException("controlled broker failure")));
        when(states.fail("282621", "BACKFILL_ERROR", "controlled broker failure"))
                .thenReturn(Mono.empty());

        var result =
                new HistoricalDailyBackfillService(api, candles, states)
                        .backfill("282621", target)
                        .block();

        assertThat(result).isEqualTo(failed);
        verify(states).createPending("282621", target);
        verify(states).start("282621", target, null);
        verify(states).fail("282621", "BACKFILL_ERROR", "controlled broker failure");
    }

    @Test
    void reachesTargetAndPersistsPageBeforeCompletion() {
        var api = Mockito.mock(KiwoomApiService.class);
        var candles = Mockito.mock(MarketDataRepository.class);
        var states = Mockito.mock(HistoricalBackfillRepository.class);
        var initial =
                new HistoricalBackfillState(
                        "005930",
                        LocalDate.of(2024, 7, 23),
                        LocalDate.of(2024, 7, 24),
                        HistoricalBackfillStatus.IN_PROGRESS,
                        null,
                        null,
                        false,
                        0,
                        0,
                        0,
                        null,
                        null);
        var finalState =
                new HistoricalBackfillState(
                        "005930",
                        LocalDate.of(2024, 7, 23),
                        LocalDate.of(2024, 7, 22),
                        HistoricalBackfillStatus.TARGET_REACHED,
                        null,
                        null,
                        false,
                        1,
                        1,
                        0,
                        null,
                        null);
        when(states.find("005930")).thenReturn(Mono.just(initial), Mono.just(finalState));
        when(candles.findOldestCandleDate("005930"))
                .thenReturn(Mono.just(LocalDate.of(2024, 7, 24)));
        when(states.persistPage(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(Boolean.class),
                        any(Integer.class),
                        any(Long.class)))
                .thenReturn(Mono.empty());
        when(states.start(eq("005930"), any(), any())).thenReturn(Mono.empty());
        when(states.finish(
                        eq("005930"),
                        any(HistoricalBackfillStatus.class),
                        nullable(HistoricalExhaustionReason.class)))
                .thenReturn(Mono.empty());
        when(api.getDailyChartPage(eq("005930"), eq(LocalDate.of(2024, 7, 23)), eq(null)))
                .thenReturn(
                        Mono.just(
                                new DailyChartPage(
                                        List.of(
                                                new DailyPriceResponse(
                                                        "20240722", 10, 12, 9, 11, 100)),
                                        false,
                                        null)));

        var result =
                new HistoricalDailyBackfillService(api, candles, states)
                        .backfill("005930", LocalDate.of(2024, 7, 23))
                        .block();

        assertThat(result.status()).isEqualTo(HistoricalBackfillStatus.TARGET_REACHED);
        verify(states)
                .persistPage(
                        any(),
                        any(),
                        eq(LocalDate.of(2024, 7, 22)),
                        any(),
                        eq(false),
                        eq(1),
                        eq(1L));
    }

    @Test
    void noProgressIsRecordedWhenContinuationDoesNotMoveOlder() {
        var api = Mockito.mock(KiwoomApiService.class);
        var candles = Mockito.mock(MarketDataRepository.class);
        var states = Mockito.mock(HistoricalBackfillRepository.class);
        var state =
                new HistoricalBackfillState(
                        "005930",
                        LocalDate.of(2015, 1, 1),
                        LocalDate.of(2024, 7, 25),
                        HistoricalBackfillStatus.IN_PROGRESS,
                        null,
                        null,
                        false,
                        0,
                        0,
                        0,
                        null,
                        null);
        var failed =
                new HistoricalBackfillState(
                        "005930",
                        LocalDate.of(2015, 1, 1),
                        LocalDate.of(2024, 7, 24),
                        HistoricalBackfillStatus.FAILED,
                        null,
                        null,
                        false,
                        1,
                        1,
                        0,
                        "NO_PROGRESS",
                        "historical traversal did not move older");
        when(states.find("005930")).thenReturn(Mono.just(state), Mono.just(failed));
        when(candles.findOldestCandleDate("005930")).thenReturn(Mono.empty());
        when(states.persistPage(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(Boolean.class),
                        any(Integer.class),
                        any(Long.class)))
                .thenReturn(Mono.empty());
        when(states.start(eq("005930"), any(), any())).thenReturn(Mono.empty());
        when(states.fail(eq("005930"), any(), any())).thenReturn(Mono.empty());
        var same =
                new DailyChartPage(
                        List.of(new DailyPriceResponse("20240724", 10, 12, 9, 11, 100)),
                        true,
                        new com.example.kiwoom.broker.kiwoom.client.ContinuationToken("next"));
        when(api.getDailyChartPage(any(), any(), any()))
                .thenReturn(Mono.just(same), Mono.just(same));
        var result =
                new HistoricalDailyBackfillService(api, candles, states)
                        .backfill("005930", LocalDate.of(2015, 1, 1))
                        .block();
        assertThat(result.status()).isEqualTo(HistoricalBackfillStatus.FAILED);
        verify(states).fail(eq("005930"), eq("NO_PROGRESS"), any());
    }

    @Test
    void terminalStateSkipsBrokerCall() {
        var api = Mockito.mock(KiwoomApiService.class);
        var candles = Mockito.mock(MarketDataRepository.class);
        var states = Mockito.mock(HistoricalBackfillRepository.class);
        var state =
                new HistoricalBackfillState(
                        "005930",
                        LocalDate.of(2015, 1, 1),
                        LocalDate.of(2014, 12, 30),
                        HistoricalBackfillStatus.TARGET_REACHED,
                        null,
                        null,
                        false,
                        2,
                        1000,
                        1,
                        null,
                        null);
        when(states.find("005930")).thenReturn(Mono.just(state));
        when(candles.findOldestCandleDate("005930")).thenReturn(Mono.empty());
        when(states.finish(eq("005930"), any(), nullable(HistoricalExhaustionReason.class)))
                .thenReturn(Mono.empty());
        var result =
                new HistoricalDailyBackfillService(api, candles, states)
                        .backfill("005930", LocalDate.of(2015, 1, 1))
                        .block();
        assertThat(result.status()).isEqualTo(HistoricalBackfillStatus.TARGET_REACHED);
        verifyNoInteractions(api);
    }

    @Test
    void brokerEndWithoutContinuationRecordsHistoryExhaustedReason() {
        var api = Mockito.mock(KiwoomApiService.class);
        var candles = Mockito.mock(MarketDataRepository.class);
        var states = Mockito.mock(HistoricalBackfillRepository.class);
        var state =
                new HistoricalBackfillState(
                        "005930",
                        LocalDate.of(2015, 1, 1),
                        LocalDate.of(2024, 7, 24),
                        HistoricalBackfillStatus.IN_PROGRESS,
                        null,
                        null,
                        false,
                        0,
                        0,
                        0,
                        null,
                        null);
        var exhausted =
                new HistoricalBackfillState(
                        "005930",
                        LocalDate.of(2015, 1, 1),
                        LocalDate.of(2024, 7, 23),
                        HistoricalBackfillStatus.HISTORY_EXHAUSTED,
                        HistoricalExhaustionReason.UNKNOWN_HISTORY_EXHAUSTED,
                        null,
                        false,
                        1,
                        1,
                        0,
                        null,
                        null);
        when(states.find("005930")).thenReturn(Mono.just(state), Mono.just(exhausted));
        when(states.persistPage(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(Boolean.class),
                        any(Integer.class),
                        any(Long.class)))
                .thenReturn(Mono.empty());
        when(states.checkpoint(
                        any(),
                        any(),
                        nullable(String.class),
                        any(Boolean.class),
                        any(Integer.class),
                        any(Long.class)))
                .thenReturn(Mono.empty());
        when(states.finish(eq("005930"), eq(HistoricalBackfillStatus.HISTORY_EXHAUSTED), any()))
                .thenReturn(Mono.empty());
        when(states.fail(eq("005930"), any(), any())).thenReturn(Mono.empty());
        when(api.getDailyChartPage(any(), any(), any()))
                .thenReturn(
                        Mono.just(
                                new DailyChartPage(
                                        List.of(
                                                new DailyPriceResponse(
                                                        "20240723", 10, 12, 9, 11, 100)),
                                        false,
                                        null)));
        var result =
                new HistoricalDailyBackfillService(api, candles, states)
                        .backfill("005930", LocalDate.of(2015, 1, 1))
                        .block();
        assertThat(result.status()).isEqualTo(HistoricalBackfillStatus.HISTORY_EXHAUSTED);
        verify(states).finish(eq("005930"), eq(HistoricalBackfillStatus.HISTORY_EXHAUSTED), any());
    }

    @Test
    void emptyBrokerPagePreservesCommittedOldestDate() {
        var api = Mockito.mock(KiwoomApiService.class);
        var candles = Mockito.mock(MarketDataRepository.class);
        var states = Mockito.mock(HistoricalBackfillRepository.class);
        var initial =
                new HistoricalBackfillState(
                        "005930",
                        LocalDate.of(2015, 1, 1),
                        LocalDate.of(2025, 10, 27),
                        HistoricalBackfillStatus.IN_PROGRESS,
                        null,
                        null,
                        false,
                        0,
                        0,
                        1,
                        null,
                        null);
        var exhausted =
                new HistoricalBackfillState(
                        "005930",
                        LocalDate.of(2015, 1, 1),
                        LocalDate.of(2025, 10, 27),
                        HistoricalBackfillStatus.HISTORY_EXHAUSTED,
                        HistoricalExhaustionReason.BROKER_HISTORY_EXHAUSTED,
                        null,
                        false,
                        1,
                        0,
                        1,
                        null,
                        null);
        when(states.find("005930")).thenReturn(Mono.just(initial), Mono.just(exhausted));
        when(states.persistPage(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(Boolean.class),
                        any(Integer.class),
                        any(Long.class)))
                .thenReturn(Mono.empty());
        when(states.checkpoint(
                        any(),
                        any(),
                        nullable(String.class),
                        any(Boolean.class),
                        any(Integer.class),
                        any(Long.class)))
                .thenReturn(Mono.empty());
        when(states.finish(eq("005930"), eq(HistoricalBackfillStatus.HISTORY_EXHAUSTED), any()))
                .thenReturn(Mono.empty());
        when(api.getDailyChartPage(any(), any(), any()))
                .thenReturn(Mono.just(new DailyChartPage(List.of(), false, null)));

        var result =
                new HistoricalDailyBackfillService(api, candles, states)
                        .backfill("005930", LocalDate.of(2015, 1, 1))
                        .block();

        assertThat(result.oldestSyncedDate()).isEqualTo(LocalDate.of(2025, 10, 27));
        verify(states)
                .checkpoint(
                        eq("005930"),
                        eq(LocalDate.of(2025, 10, 27)),
                        nullable(String.class),
                        eq(false),
                        eq(1),
                        eq(0L));
    }

    @Test
    void stateModelContainsAllContractStatuses() {
        assertThat(HistoricalBackfillStatus.values())
                .containsExactlyInAnyOrder(
                        HistoricalBackfillStatus.PENDING,
                        HistoricalBackfillStatus.IN_PROGRESS,
                        HistoricalBackfillStatus.TARGET_REACHED,
                        HistoricalBackfillStatus.HISTORY_EXHAUSTED,
                        HistoricalBackfillStatus.ALREADY_SATISFIED,
                        HistoricalBackfillStatus.FAILED);
    }

    @Test
    void resumeUsesCommittedOldestDateInsteadOfPersistedContinuationKey() {
        var api = Mockito.mock(KiwoomApiService.class);
        var candles = Mockito.mock(MarketDataRepository.class);
        var states = Mockito.mock(HistoricalBackfillRepository.class);
        var state =
                new HistoricalBackfillState(
                        "005930",
                        LocalDate.of(2015, 1, 1),
                        LocalDate.of(2024, 7, 24),
                        HistoricalBackfillStatus.IN_PROGRESS,
                        null,
                        "stale-token",
                        true,
                        1,
                        1,
                        1,
                        null,
                        null);
        var failed =
                new HistoricalBackfillState(
                        "005930",
                        LocalDate.of(2015, 1, 1),
                        LocalDate.of(2024, 7, 24),
                        HistoricalBackfillStatus.FAILED,
                        null,
                        null,
                        false,
                        1,
                        1,
                        1,
                        "NO_PROGRESS",
                        "stopped");
        when(states.find("005930")).thenReturn(Mono.just(state), Mono.just(failed));
        when(api.getDailyChartPage(eq("005930"), eq(LocalDate.of(2024, 7, 23)), eq(null)))
                .thenReturn(Mono.error(new IllegalStateException("stop")));
        when(states.fail(eq("005930"), eq("BACKFILL_ERROR"), any())).thenReturn(Mono.empty());
        var result =
                new HistoricalDailyBackfillService(api, candles, states)
                        .backfill("005930", LocalDate.of(2015, 1, 1))
                        .block();
        assertThat(result.status()).isEqualTo(HistoricalBackfillStatus.FAILED);
        verify(api).getDailyChartPage(eq("005930"), eq(LocalDate.of(2024, 7, 23)), eq(null));
        verify(api, times(1)).getDailyChartPage(any(), any(), any());
    }

    @Test
    void permanentErrorFailsWithoutRetry() {
        var api = Mockito.mock(KiwoomApiService.class);
        var candles = Mockito.mock(MarketDataRepository.class);
        var states = Mockito.mock(HistoricalBackfillRepository.class);
        var state =
                new HistoricalBackfillState(
                        "005931",
                        LocalDate.of(2015, 1, 1),
                        null,
                        HistoricalBackfillStatus.IN_PROGRESS,
                        null,
                        null,
                        false,
                        0,
                        0,
                        0,
                        null,
                        null);
        var failed =
                new HistoricalBackfillState(
                        "005931",
                        LocalDate.of(2015, 1, 1),
                        null,
                        HistoricalBackfillStatus.FAILED,
                        null,
                        null,
                        false,
                        0,
                        0,
                        1,
                        "VALIDATION",
                        "bad request");
        when(states.find("005931")).thenReturn(Mono.just(state), Mono.just(failed));
        when(api.getDailyChartPage(any(), any(), any()))
                .thenReturn(Mono.error(new IllegalArgumentException("bad request")));
        when(states.fail(eq("005931"), eq("BACKFILL_ERROR"), any())).thenReturn(Mono.empty());
        var result =
                new HistoricalDailyBackfillService(api, candles, states)
                        .backfill("005931", LocalDate.of(2015, 1, 1))
                        .block();
        assertThat(result.status()).isEqualTo(HistoricalBackfillStatus.FAILED);
        verify(api, times(1)).getDailyChartPage(any(), any(), any());
    }

    @Test
    void repeatedTargetRerunIsIdempotentAndSkipsHistoricalTraversal() {
        var api = Mockito.mock(KiwoomApiService.class);
        var candles = Mockito.mock(MarketDataRepository.class);
        var states = Mockito.mock(HistoricalBackfillRepository.class);
        var terminal =
                new HistoricalBackfillState(
                        "005932",
                        LocalDate.of(2015, 1, 1),
                        LocalDate.of(2014, 12, 31),
                        HistoricalBackfillStatus.TARGET_REACHED,
                        null,
                        null,
                        false,
                        4,
                        200,
                        2,
                        null,
                        null);
        when(states.find("005932")).thenReturn(Mono.just(terminal));
        var result =
                new HistoricalDailyBackfillService(api, candles, states)
                        .backfill("005932", LocalDate.of(2015, 1, 1))
                        .block();
        assertThat(result).isEqualTo(terminal);
        verifyNoInteractions(api);
    }
}
