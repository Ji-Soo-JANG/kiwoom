package com.example.kiwoom.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class AlertEvaluationSchedulerTest {

    @Mock private AlertService alerts;
    @Mock private MarketCalendarService calendar;

    @Test
    void skipsEvaluationWhileMarketIsClosed() {
        when(calendar.isOpenNow()).thenReturn(false);

        new AlertEvaluationScheduler(alerts, calendar, "admin").evaluate();

        verify(alerts, never()).evaluate("admin");
    }

    @Test
    void evaluatesAlertsWhileMarketIsOpen() {
        when(calendar.isOpenNow()).thenReturn(true);
        when(alerts.evaluate("admin")).thenReturn(Flux.empty());

        new AlertEvaluationScheduler(alerts, calendar, "admin").evaluate();

        verify(alerts).evaluate("admin");
    }
}
