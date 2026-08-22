package com.example.kiwoom.service.strategy;

import com.example.kiwoom.dto.DailyPriceResponse;
import com.example.kiwoom.dto.MarketRankingItem;
import com.example.kiwoom.dto.StrategyCandidate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 단기 급락 뒤 수개월~수년 횡보하고 낙폭의 일부를 회복한 후 눌린 종목을 찾는다. */
@Component
public class MultiPeriodRecoveryPullbackStrategy implements StockStrategy {
    public static final String VERSION_KEY = "drop-multi-base-recovery-pullback-v2";
    public static final int REQUIRED_HISTORY_DAYS = 1500;
    private static final int[] BASE_WINDOWS = {60, 120, 240, 480, 720, 1200};
    private static final int BREAKOUT_LOOKBACK = 15;
    private static final int DROP_LOOKBACK = 30;

    @Override
    public String versionKey() {
        return VERSION_KEY;
    }

    @Override
    public int requiredHistoryDays() {
        return REQUIRED_HISTORY_DAYS;
    }

    @Override
    public StrategyCandidate analyze(
            MarketRankingItem stock, List<DailyPriceResponse> source, int preferredBaseDays) {
        List<DailyPriceResponse> prices =
                source.stream().sorted(Comparator.comparing(DailyPriceResponse::getDate)).toList();
        Analysis best = null;
        for (int baseDays : windows(preferredBaseDays)) {
            if (prices.size() < baseDays + DROP_LOOKBACK + 2) continue;
            int firstBreakout =
                    Math.max(baseDays + DROP_LOOKBACK, prices.size() - BREAKOUT_LOOKBACK);
            for (int breakoutIndex = firstBreakout;
                    breakoutIndex < prices.size() - 1;
                    breakoutIndex++) {
                Analysis analysis = analyzeWindow(prices, breakoutIndex, baseDays);
                if (best == null || analysis.score() > best.score()) best = analysis;
            }
        }
        if (best == null) return insufficient(stock, prices.size());
        return new StrategyCandidate(
                stock.code(),
                stock.name(),
                stock.currentPrice(),
                best.score(),
                best.score() >= 75,
                percent(best.dropRate()),
                percent(best.boxRangeRate()),
                best.volumeSpikeCount(),
                percent(best.recoveryRatio()),
                percent(best.pullbackRate()),
                List.copyOf(best.conditions()));
    }

    private Analysis analyzeWindow(
            List<DailyPriceResponse> prices, int breakoutIndex, int baseDays) {
        int baseStart = breakoutIndex - baseDays;
        List<DailyPriceResponse> preDrop =
                prices.subList(Math.max(0, baseStart - DROP_LOOKBACK), baseStart);
        List<DailyPriceResponse> base = prices.subList(baseStart, breakoutIndex);
        List<DailyPriceResponse> afterBreakout = prices.subList(breakoutIndex, prices.size());

        long preDropHigh =
                preDrop.stream().mapToLong(DailyPriceResponse::getHighPrice).max().orElse(0);
        long baseLow = percentile(base, 0.10);
        long baseHigh = percentile(base, 0.90);
        long medianVolume = median(base.stream().map(DailyPriceResponse::getVolume).toList());
        long peak =
                afterBreakout.stream().mapToLong(DailyPriceResponse::getHighPrice).max().orElse(0);
        long latest = prices.get(prices.size() - 1).getClosePrice();

        double drop = ratio(baseLow, preDropHigh) - 1;
        double boxRange = ratio(baseHigh, baseLow) - 1;
        double recovery =
                preDropHigh <= baseLow ? 0 : (double) (peak - baseLow) / (preDropHigh - baseLow);
        double pullback = ratio(latest, peak) - 1;
        int spikes =
                (int)
                        base.stream()
                                .filter(
                                        day ->
                                                medianVolume > 0
                                                        && day.getVolume() >= medianVolume * 2.5)
                                .count();
        double coverage =
                base.stream()
                                .filter(
                                        day ->
                                                day.getClosePrice() >= baseLow
                                                        && day.getClosePrice() <= baseHigh)
                                .count()
                        / (double) base.size();
        double baseSlope = slope(base);
        double pullbackVolume =
                afterBreakout.stream()
                        .skip(1)
                        .mapToLong(DailyPriceResponse::getVolume)
                        .average()
                        .orElse(0);
        double peakVolume =
                afterBreakout.stream().mapToLong(DailyPriceResponse::getVolume).max().orElse(0);

        int score = 0;
        List<String> conditions = new ArrayList<>();
        if (drop <= -0.30) {
            score += 20;
            conditions.add("박스권 진입 전 단기 30% 이상 급락");
        }
        double allowedRange = baseDays >= 480 ? 0.45 : baseDays >= 240 ? 0.38 : 0.32;
        if (boxRange <= allowedRange && coverage >= 0.80 && Math.abs(baseSlope) <= 0.0008) {
            score += 20;
            conditions.add(baseDays + "거래일 장기 박스권");
        }
        if (spikes >= 2) {
            score += 15;
            conditions.add("박스권 내 간헐적 거래량 급증 " + spikes + "회");
        }
        if (recovery >= 0.15 && recovery <= 0.30) {
            score += 25;
            conditions.add("이전 낙폭의 " + Math.round(recovery * 100) + "% 회복");
        }
        if (pullback <= -0.04 && pullback >= -0.12 && latest >= baseHigh * 0.97) {
            score += 15;
            conditions.add("회복 고점 이후 눌림목");
        }
        if (peakVolume > 0 && pullbackVolume / peakVolume <= 0.65) {
            score += 5;
            conditions.add("눌림 구간 거래량 감소");
        }
        return new Analysis(score, drop, boxRange, spikes, recovery, pullback, conditions);
    }

    private Set<Integer> windows(int preferred) {
        Set<Integer> result = new LinkedHashSet<>();
        if (preferred >= 60 && preferred <= 1200) result.add(preferred);
        for (int window : BASE_WINDOWS) result.add(window);
        return result;
    }

    private long percentile(List<DailyPriceResponse> prices, double percentile) {
        List<Long> values =
                prices.stream().map(DailyPriceResponse::getClosePrice).sorted().toList();
        return values.get(
                Math.min(values.size() - 1, (int) Math.floor((values.size() - 1) * percentile)));
    }

    private long median(List<Long> values) {
        List<Long> sorted = values.stream().sorted().toList();
        return sorted.isEmpty() ? 0 : sorted.get(sorted.size() / 2);
    }

    private double slope(List<DailyPriceResponse> prices) {
        if (prices.size() < 2 || prices.get(0).getClosePrice() == 0) return 1;
        return (ratio(prices.get(prices.size() - 1).getClosePrice(), prices.get(0).getClosePrice())
                        - 1)
                / prices.size();
    }

    private double ratio(long value, long reference) {
        return reference <= 0 ? 0 : (double) value / reference;
    }

    private double percent(double value) {
        return Math.round(value * 10_000.0) / 100.0;
    }

    private StrategyCandidate insufficient(MarketRankingItem stock, int count) {
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
                List.of("장기 전략 분석용 일봉 부족 (현재 " + count + "개)"));
    }

    private record Analysis(
            int score,
            double dropRate,
            double boxRangeRate,
            int volumeSpikeCount,
            double recoveryRatio,
            double pullbackRate,
            List<String> conditions) {}
}
