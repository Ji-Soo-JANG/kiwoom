package com.example.kiwoom.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class SwingPositionMonitoringSchedulerTest {
    @Test
    void doesNotReadPositionsOutsideMarketHours() {
        PaperTradeCycleService cycles = mock(PaperTradeCycleService.class);
        MarketCalendarService calendar = mock(MarketCalendarService.class);
        when(calendar.isOpenNow()).thenReturn(false);
        var scheduler =
                new SwingPositionMonitoringScheduler(
                        cycles,
                        mock(KiwoomApiService.class),
                        mock(PaperOrderService.class),
                        calendar);

        scheduler.monitor();

        verify(cycles, never()).findAll();
    }
}
