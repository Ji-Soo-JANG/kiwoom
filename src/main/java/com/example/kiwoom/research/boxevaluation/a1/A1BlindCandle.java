package com.example.kiwoom.research.boxevaluation.a1;

import java.time.LocalDate;

/** Candle projection for the initial blind view; identity and volume are intentionally absent. */
public record A1BlindCandle(
        LocalDate tradeDate, long openPrice, long highPrice, long lowPrice, long closePrice) {}
