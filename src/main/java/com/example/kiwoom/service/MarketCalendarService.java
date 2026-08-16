package com.example.kiwoom.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MarketCalendarService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final Set<LocalDate> holidays;

    public MarketCalendarService(@Value("${app.market.holidays:}") String holidays) {
        this.holidays =
                holidays.isBlank()
                        ? Set.of()
                        : java.util.Arrays.stream(holidays.split(","))
                                .map(String::trim)
                                .filter(value -> !value.isEmpty())
                                .map(LocalDate::parse)
                                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isOpenNow() {
        var now = java.time.ZonedDateTime.now(SEOUL);
        return isTradingDay(now.toLocalDate())
                && !now.toLocalTime().isBefore(LocalTime.of(9, 0))
                && !now.toLocalTime().isAfter(LocalTime.of(15, 30));
    }

    public boolean isTradingDay(LocalDate date) {
        return date.getDayOfWeek() != DayOfWeek.SATURDAY
                && date.getDayOfWeek() != DayOfWeek.SUNDAY
                && !holidays.contains(date);
    }
}
