package com.example.kiwoom.research.boxevaluation.a1;

import java.time.LocalDate;
import java.util.List;

public record A1BlindPayload(LocalDate cutoffDate, List<A1BlindCandle> candles) {
    public A1BlindPayload {
        candles = List.copyOf(candles);
    }
}
