package com.example.kiwoom.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class MarketCalendarServiceTest {

    private final MarketCalendarService calendar =
            new MarketCalendarService("2026-08-17, 2026-10-05");

    @Test
    void distinguishesTradingDaysFromWeekendsAndConfiguredHolidays() {
        assertThat(calendar.isTradingDay(LocalDate.of(2026, 8, 18))).isTrue();
        assertThat(calendar.isTradingDay(LocalDate.of(2026, 8, 16))).isFalse();
        assertThat(calendar.isTradingDay(LocalDate.of(2026, 8, 17))).isFalse();
    }

    @Test
    void acceptsAnEmptyHolidayConfiguration() {
        assertThat(new MarketCalendarService("").isTradingDay(LocalDate.of(2026, 8, 17))).isTrue();
    }
}
