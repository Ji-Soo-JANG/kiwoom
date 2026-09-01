package com.example.kiwoom.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.kiwoom.dto.DailyPriceResponse;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class HistoricalCandleValidatorTest {
    @Test
    void acceptsValidPageAndRejectsFutureOrInvalidRows() {
        assertDoesNotThrow(
                () ->
                        HistoricalCandleValidator.validate(
                                "005930",
                                LocalDate.of(2024, 7, 23),
                                List.of(new DailyPriceResponse("20240723", 10, 12, 9, 11, 100))));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        HistoricalCandleValidator.validate(
                                "005930",
                                LocalDate.of(2024, 7, 23),
                                List.of(new DailyPriceResponse("20240724", 10, 12, 9, 11, 100))));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        HistoricalCandleValidator.validate(
                                "005930",
                                null,
                                List.of(new DailyPriceResponse("20240723", 10, 9, 12, 11, 100))));
    }

    @Test
    void rejectsDuplicateDatesAndNegativeVolume() {
        DailyPriceResponse one = new DailyPriceResponse("20240723", 10, 12, 9, 11, 100);
        DailyPriceResponse duplicate = new DailyPriceResponse("20240723", 10, 12, 9, 11, 100);
        assertThrows(
                IllegalArgumentException.class,
                () -> HistoricalCandleValidator.validate("005930", null, List.of(one, duplicate)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        HistoricalCandleValidator.validate(
                                "005930",
                                null,
                                List.of(new DailyPriceResponse("20240722", 10, 12, 9, 11, -1))));
    }
}
