package com.example.kiwoom.strategy.implementation;

import com.example.kiwoom.dto.DailyPriceResponse;
import com.example.kiwoom.dto.MarketRankingItem;
import com.example.kiwoom.strategy.StockStrategy;
import com.example.kiwoom.strategy.model.StrategyCandidate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** 과거 완성 패턴이 아니라 최신 일봉이 회복 후 눌림 단계인 종목만 통과시키는 전략이다. */
@Component
public class CurrentRecoveryPullbackStrategy implements StockStrategy {
    public static final String VERSION_KEY = "drop-multi-base-current-pullback-v3";
    private final MultiPeriodRecoveryPullbackStrategy detector =
            new MultiPeriodRecoveryPullbackStrategy();

    @Override
    public String versionKey() {
        return VERSION_KEY;
    }

    @Override
    public int requiredHistoryDays() {
        return detector.requiredHistoryDays();
    }

    @Override
    public StrategyCandidate analyze(
            MarketRankingItem stock, List<DailyPriceResponse> prices, int baseDays) {
        MultiPeriodRecoveryPullbackStrategy.Evaluation evaluation =
                detector.evaluate(stock, prices, baseDays);
        StrategyCandidate candidate = evaluation.candidate();
        List<String> conditions = new ArrayList<>(candidate.matchedConditions());
        boolean currentPattern = evaluation.currentPattern();
        if (currentPattern) conditions.add("최신 일봉이 현재 눌림 단계");
        else if (candidate.score() > 0) conditions.add("현재 진행 중인 전체 패턴이 아니므로 제외");

        return new StrategyCandidate(
                candidate.code(),
                candidate.name(),
                candidate.currentPrice(),
                candidate.score(),
                currentPattern,
                candidate.drawdownRate(),
                candidate.boxRangeRate(),
                candidate.volumeSpikeCount(),
                candidate.breakoutRate(),
                candidate.pullbackRate(),
                List.copyOf(conditions));
    }
}
