package com.example.kiwoom.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TradingWorkflowSchedulerTest {
    @Test
    void doesNotRunOnClosedMarketDay() {
        var marketData = mock(MarketDataCollectionService.class);
        var scans = mock(StrategyScanService.class);
        var observations = mock(ObservationService.class);
        var calendar = mock(MarketCalendarService.class);
        when(calendar.isTradingDay(any(LocalDate.class))).thenReturn(false);

        new TradingWorkflowScheduler(marketData, scans, observations, calendar, 100).run();

        verify(marketData, never()).synchronize(100);
    }
}
