package com.example.kiwoom.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.kiwoom.dto.DailyPriceResponse;
import com.example.kiwoom.dto.MarketDataSyncStatus;
import com.example.kiwoom.dto.StockSearchResult;
import com.example.kiwoom.repository.MarketDataRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class FullMarketDataCollectionServiceTest {

    private final KiwoomApiService kiwoom = mock(KiwoomApiService.class);
    private final MarketDataRepository repository = mock(MarketDataRepository.class);
    private final FullMarketDataCollectionService service =
            new FullMarketDataCollectionService(kiwoom, repository, 1, false, false);

    private static StockSearchResult stock(String code, String name) {
        return new StockSearchResult(code, name, "KOSPI");
    }

    private static DailyPriceResponse candle(String date) {
        return new DailyPriceResponse(date, 100, 110, 90, 105, 1000);
    }

    private static MarketDataSyncStatus status(int processed, int succeeded, int failed) {
        return new MarketDataSyncStatus(
                2,
                10,
                2,
                0,
                LocalDate.of(2026, 8, 16),
                processed,
                succeeded,
                failed,
                false,
                Instant.now());
    }

    private static KiwoomApiService.StockCatalogStatus catalogStatus(int count) {
        return new KiwoomApiService.StockCatalogStatus(Instant.now(), count);
    }

    @Test
    void savesCatalogAndEveryStockDailyPrices() {
        StockSearchResult first = stock("000001", "가");
        StockSearchResult second = stock("000002", "나");
        when(kiwoom.refreshStockCatalog()).thenReturn(Mono.just(catalogStatus(2)));
        when(kiwoom.getStockCatalog()).thenReturn(Mono.just(List.of(first, second)));
        when(kiwoom.getDailyPrices("000001", null, 500))
                .thenReturn(Mono.just(List.of(candle("20260814"), candle("20260816"))));
        when(kiwoom.getDailyPrices("000002", null, 500))
                .thenReturn(Mono.just(List.of(candle("20260816"))));
        when(repository.saveStocks(any(Flux.class))).thenReturn(Mono.empty());
        when(repository.saveCandles(eq("000001"), any(Flux.class))).thenReturn(Mono.empty());
        when(repository.saveCandles(eq("000002"), any(Flux.class))).thenReturn(Mono.empty());
        when(repository.markSuccess(eq("000001"), any(LocalDate.class))).thenReturn(Mono.empty());
        when(repository.markSuccess(eq("000002"), any(LocalDate.class))).thenReturn(Mono.empty());
        when(repository.status(anyInt(), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(Mono.just(status(2, 2, 0)));

        MarketDataSyncStatus result = service.synchronizeAll().block();

        assertNotNull(result);
        assertEquals(2, result.processedInLastRun());
        verify(repository).saveStocks(any(Flux.class));
        verify(repository).saveCandles(eq("000001"), any(Flux.class));
        verify(repository).saveCandles(eq("000002"), any(Flux.class));
        verify(repository, times(2)).markSuccess(anyString(), any(LocalDate.class));
        verify(repository, never()).markFailure(anyString(), anyString());
    }

    @Test
    void continuesWhenSingleStockFails() {
        StockSearchResult first = stock("000001", "가");
        StockSearchResult second = stock("000002", "나");
        when(kiwoom.refreshStockCatalog()).thenReturn(Mono.just(catalogStatus(2)));
        when(kiwoom.getStockCatalog()).thenReturn(Mono.just(List.of(first, second)));
        when(kiwoom.getDailyPrices("000001", null, 500))
                .thenReturn(Mono.error(new RuntimeException("키움 조회 실패")));
        when(kiwoom.getDailyPrices("000002", null, 500))
                .thenReturn(Mono.just(List.of(candle("20260816"))));
        when(repository.saveStocks(any(Flux.class))).thenReturn(Mono.empty());
        when(repository.markFailure(eq("000001"), anyString())).thenReturn(Mono.empty());
        when(repository.saveCandles(eq("000002"), any(Flux.class))).thenReturn(Mono.empty());
        when(repository.markSuccess(eq("000002"), any(LocalDate.class))).thenReturn(Mono.empty());
        when(repository.status(anyInt(), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(Mono.just(status(2, 1, 1)));

        MarketDataSyncStatus result = service.synchronizeAll().block();

        assertNotNull(result);
        assertEquals(1, result.succeededInLastRun());
        assertEquals(1, result.failedInLastRun());
        verify(repository).markFailure(eq("000001"), anyString());
        verify(repository).markSuccess(eq("000002"), any(LocalDate.class));
    }

    @Test
    void fallsBackToLastKnownCatalogWhenRefreshFails() {
        StockSearchResult first = stock("000001", "가");
        when(kiwoom.refreshStockCatalog())
                .thenReturn(Mono.just(catalogStatus(1)))
                .thenReturn(Mono.error(new RuntimeException("인증 실패")));
        when(kiwoom.getStockCatalog()).thenReturn(Mono.just(List.of(first)));
        when(kiwoom.getDailyPrices("000001", null, 500))
                .thenReturn(Mono.just(List.of(candle("20260816"))));
        when(repository.saveStocks(any(Flux.class))).thenReturn(Mono.empty());
        when(repository.saveCandles(eq("000001"), any(Flux.class))).thenReturn(Mono.empty());
        when(repository.markSuccess(eq("000001"), any(LocalDate.class))).thenReturn(Mono.empty());
        when(repository.status(anyInt(), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(Mono.just(status(1, 1, 0)));

        MarketDataSyncStatus firstRun = service.synchronizeAll().block();
        MarketDataSyncStatus secondRun = service.synchronizeAll().block();

        assertNotNull(firstRun);
        assertNotNull(secondRun);
        verify(kiwoom, times(2)).refreshStockCatalog();
        verify(kiwoom).getStockCatalog();
        verify(repository, times(2)).saveCandles(eq("000001"), any(Flux.class));
        verify(repository, times(2)).markSuccess(eq("000001"), any(LocalDate.class));
    }

    @Test
    void scheduledRunIsNoOpWhenDisabled() {
        service.scheduledSynchronizeAll();

        verifyNoInteractions(kiwoom);
        verifyNoInteractions(repository);
    }
}
