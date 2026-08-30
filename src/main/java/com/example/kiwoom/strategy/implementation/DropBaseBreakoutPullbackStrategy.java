package com.example.kiwoom.strategy.implementation;

import com.example.kiwoom.dto.DailyPriceResponse;
import com.example.kiwoom.dto.MarketRankingItem;
import com.example.kiwoom.strategy.StockStrategy;
import com.example.kiwoom.strategy.model.StrategyCandidate;
import com.example.kiwoom.strategy.service.StrategyPatternDetector;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DropBaseBreakoutPullbackStrategy implements StockStrategy {
    public static final String VERSION_KEY = "drop-base-breakout-pullback-v1";
    private final StrategyPatternDetector detector = new StrategyPatternDetector();

    @Override
    public String versionKey() {
        return VERSION_KEY;
    }

    @Override
    public StrategyCandidate analyze(
            MarketRankingItem stock, List<DailyPriceResponse> prices, int baseDays) {
        return detector.analyze(stock, prices, baseDays);
    }
}
