package com.example.kiwoom.broker.kiwoom.client;

import com.example.kiwoom.dto.DailyPriceResponse;
import java.util.List;

public record DailyChartPage(
        List<DailyPriceResponse> candles,
        boolean continuationAvailable,
        ContinuationToken continuationToken) {
    public DailyChartPage {
        candles = candles == null ? List.of() : List.copyOf(candles);
        if (!continuationAvailable) continuationToken = null;
    }
}
