package com.example.kiwoom.service.strategy;

import com.example.kiwoom.dto.DailyPriceResponse;
import com.example.kiwoom.dto.MarketRankingItem;
import com.example.kiwoom.dto.StrategyCandidate;
import java.util.List;

public interface StockStrategy {
    String versionKey();

    StrategyCandidate analyze(
            MarketRankingItem stock, List<DailyPriceResponse> prices, int baseDays);
}
