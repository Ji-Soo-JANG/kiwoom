package com.example.kiwoom.research.boxevaluation.a1;

import java.time.LocalDate;
import java.util.List;

public record A1EligibleSymbol(String code, String market, List<LocalDate> eligibleCutoffDates) {
    public A1EligibleSymbol {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("code is required");
        if (!"KOSPI".equals(market) && !"KOSDAQ".equals(market)) {
            throw new IllegalArgumentException("unsupported market: " + market);
        }
        eligibleCutoffDates =
                eligibleCutoffDates == null ? List.of() : List.copyOf(eligibleCutoffDates);
    }
}
