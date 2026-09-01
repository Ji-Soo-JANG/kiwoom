package com.example.kiwoom.service;

import com.example.kiwoom.dto.DailyPriceResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class HistoricalCandleValidator {
    private HistoricalCandleValidator() {}

    static void validate(String code, LocalDate baseDate, List<DailyPriceResponse> candles) {
        if (candles == null || candles.isEmpty()) return;
        Set<String> dates = new HashSet<>();
        for (DailyPriceResponse candle : candles) {
            if (candle == null || candle.getDate() == null || candle.getDate().isBlank())
                throw new IllegalArgumentException("malformed candle date");
            LocalDate date;
            try {
                date = LocalDate.parse(candle.getDate(), DateTimeFormatter.BASIC_ISO_DATE);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("malformed candle date", e);
            }
            if (!dates.add(candle.getDate()))
                throw new IllegalArgumentException("duplicate candle date");
            if (baseDate != null && date.isAfter(baseDate))
                throw new IllegalArgumentException("candle date is after requested base date");
            long open = candle.getOpenPrice(), high = candle.getHighPrice();
            long low = candle.getLowPrice(), close = candle.getClosePrice();
            if (high < low || high < open || high < close || low > open || low > close)
                throw new IllegalArgumentException("invalid OHLC relationship");
            if (open < 0 || high < 0 || low < 0 || close < 0 || candle.getVolume() < 0)
                throw new IllegalArgumentException("negative OHLCV value");
        }
    }
}
