package com.example.kiwoom.strategy;

import com.example.kiwoom.dto.DailyPriceResponse;
import com.example.kiwoom.dto.MarketRankingItem;
import com.example.kiwoom.strategy.model.StrategyCandidate;
import java.util.List;

public interface StockStrategy {
    String versionKey();

    default int requiredHistoryDays() {
        return 250;
    }

    StrategyCandidate analyze(
            MarketRankingItem stock, List<DailyPriceResponse> prices, int baseDays);
}
