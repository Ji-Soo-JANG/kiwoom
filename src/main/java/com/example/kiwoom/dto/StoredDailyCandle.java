package com.example.kiwoom.dto;

import java.time.LocalDate;

public record StoredDailyCandle(
        String code,
        LocalDate tradeDate,
        long openPrice,
        long highPrice,
        long lowPrice,
        long closePrice,
        long volume) {}
