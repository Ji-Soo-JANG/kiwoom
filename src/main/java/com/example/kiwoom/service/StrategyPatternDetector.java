package com.example.kiwoom.service;

import com.example.kiwoom.dto.DailyPriceResponse;
import com.example.kiwoom.dto.MarketRankingItem;
import com.example.kiwoom.dto.StrategyCandidate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class StrategyPatternDetector {
    private static final int BASE_DAYS = 60;
    private static final int BREAKOUT_LOOKBACK = 10;

    public StrategyCandidate analyze(
            MarketRankingItem stock, List<DailyPriceResponse> sourcePrices) {
        List<DailyPriceResponse> prices =
                sourcePrices.stream()
                        .sorted(Comparator.comparing(DailyPriceResponse::getDate))
                        .toList();
        if (prices.size() < BASE_DAYS + BREAKOUT_LOOKBACK + 20) {
            return insufficient(stock);
        }

        Analysis best = null;
        int firstBreakout = Math.max(BASE_DAYS + 20, prices.size() - BREAKOUT_LOOKBACK);
        for (int index = firstBreakout; index < prices.size() - 1; index++) {
            Analysis analysis = analyzeBreakout(prices, index);
            if (best == null || analysis.score > best.score) best = analysis;
        }
        if (best == null) return insufficient(stock);

        return new StrategyCandidate(
                stock.code(),
                stock.name(),
                stock.currentPrice(),
                best.score,
                best.score >= 70,
                round(best.drawdownRate),
                round(best.boxRangeRate),
                best.volumeSpikeCount,
                round(best.breakoutRate),
                round(best.pullbackRate),
                List.copyOf(best.matchedConditions));
    }

    private Analysis analyzeBreakout(List<DailyPriceResponse> prices, int breakoutIndex) {
        int baseStart = breakoutIndex - BASE_DAYS;
        List<DailyPriceResponse> base = prices.subList(baseStart, breakoutIndex);
        List<DailyPriceResponse> history = prices.subList(0, baseStart);
        DailyPriceResponse breakout = prices.get(breakoutIndex);
        DailyPriceResponse previous = prices.get(breakoutIndex - 1);
        DailyPriceResponse latest = prices.get(prices.size() - 1);

        long historicHigh =
                history.stream().mapToLong(DailyPriceResponse::getHighPrice).max().orElse(0);
        long baseHigh = base.stream().mapToLong(DailyPriceResponse::getHighPrice).max().orElse(0);
        long baseLow = base.stream().mapToLong(DailyPriceResponse::getLowPrice).min().orElse(0);
        long baseMedianVolume = median(base.stream().map(DailyPriceResponse::getVolume).toList());
        int volumeSpikes =
                (int)
                        base.stream()
                                .filter(
                                        day ->
                                                baseMedianVolume > 0
                                                        && day.getVolume()
                                                                >= baseMedianVolume * 2.5)
                                .count();

        double drawdown = historicHigh == 0 ? 0 : percentage(baseLow, historicHigh);
        double boxRange = baseLow == 0 ? 1 : (double) (baseHigh - baseLow) / baseLow;
        double breakoutRate = percentage(breakout.getClosePrice(), previous.getClosePrice());
        long postBreakoutHigh =
                prices.subList(breakoutIndex, prices.size()).stream()
                        .mapToLong(DailyPriceResponse::getHighPrice)
                        .max()
                        .orElse(breakout.getHighPrice());
        double pullback = percentage(latest.getClosePrice(), postBreakoutHigh);
        double breakoutRelativeVolume =
                baseMedianVolume == 0 ? 0 : (double) breakout.getVolume() / baseMedianVolume;
        double adjustmentVolumeRatio =
                prices.subList(breakoutIndex + 1, prices.size()).stream()
                                .mapToLong(DailyPriceResponse::getVolume)
                                .average()
                                .orElse(breakout.getVolume())
                        / Math.max(1, breakout.getVolume());

        int score = 0;
        List<String> matched = new ArrayList<>();
        if (drawdown <= -0.20) {
            score += 15;
            matched.add("과거 고점 대비 20% 이상 하락");
        }
        if (boxRange <= 0.30) {
            score += 15;
            matched.add("60거래일 박스권 횡보");
        }
        if (volumeSpikes >= 2) {
            score += 15;
            matched.add("횡보 중 거래량 급증 " + volumeSpikes + "회");
        }
        if (breakoutRate >= 0.05
                && breakoutRate <= 0.18
                && breakout.getClosePrice() >= baseHigh * 1.01
                && breakoutRelativeVolume >= 2.0) {
            score += 25;
            matched.add("거래량을 동반한 초기 박스 돌파");
        }
        if (pullback <= -0.02 && pullback >= -0.12 && latest.getClosePrice() >= baseHigh * 0.97) {
            score += 20;
            matched.add("돌파선 위 눌림목");
        }
        if (adjustmentVolumeRatio <= 0.70) {
            score += 10;
            matched.add("조정 구간 거래량 감소");
        }
        return new Analysis(
                score, drawdown, boxRange, volumeSpikes, breakoutRate, pullback, matched);
    }

    private StrategyCandidate insufficient(MarketRankingItem stock) {
        return new StrategyCandidate(
                stock.code(),
                stock.name(),
                stock.currentPrice(),
                0,
                false,
                0,
                0,
                0,
                0,
                0,
                List.of("분석할 일봉 데이터 부족"));
    }

    private long median(List<Long> values) {
        List<Long> sorted = values.stream().sorted().toList();
        return sorted.isEmpty() ? 0 : sorted.get(sorted.size() / 2);
    }

    private double percentage(long value, long reference) {
        return reference == 0 ? 0 : (double) value / reference - 1;
    }

    private double round(double value) {
        return Math.round(value * 10_000.0) / 100.0;
    }

    private record Analysis(
            int score,
            double drawdownRate,
            double boxRangeRate,
            int volumeSpikeCount,
            double breakoutRate,
            double pullbackRate,
            List<String> matchedConditions) {}
}
