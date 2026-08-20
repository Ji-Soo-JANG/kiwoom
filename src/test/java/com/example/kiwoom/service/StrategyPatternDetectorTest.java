package com.example.kiwoom.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.kiwoom.dto.DailyPriceResponse;
import com.example.kiwoom.dto.MarketRankingItem;
import com.example.kiwoom.dto.StrategyCandidate;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StrategyPatternDetectorTest {
    private final StrategyPatternDetector detector = new StrategyPatternDetector();

    @Test
    void detectsDropBaseVolumeBreakoutAndPullbackPattern() {
        List<DailyPriceResponse> prices = new ArrayList<>();
        LocalDate start = LocalDate.of(2026, 1, 1);
        for (int index = 0; index < 30; index++) {
            prices.add(day(start.plusDays(index), 180, 200, 175, 180, 100));
        }
        for (int index = 0; index < 60; index++) {
            long volume = index == 15 || index == 42 ? 300 : 100;
            prices.add(day(start.plusDays(30L + index), 103, 110, 100, 105, volume));
        }
        prices.add(day(start.plusDays(90), 106, 121, 106, 120, 500));
        prices.add(day(start.plusDays(91), 118, 122, 114, 117, 120));
        prices.add(day(start.plusDays(92), 116, 118, 113, 115, 100));

        StrategyCandidate result =
                detector.analyze(new MarketRankingItem("005930", "테스트종목", 115, -1.0, 100), prices);

        assertTrue(result.qualified());
        assertEquals(100, result.score());
        assertEquals(2, result.volumeSpikeCount());
        assertTrue(result.matchedConditions().contains("돌파선 위 눌림목"));
    }

    @Test
    void appliesCustomBaseDaysToBoxRangeAnalysis() {
        // 30일 하락 후 45일 횡보: 기본 60거래일 기준으로는 데이터가 부족하지만
        // 45거래일 기준으로 조절하면 박스권 횡보 패턴을 감지한다.
        List<DailyPriceResponse> prices = new ArrayList<>();
        LocalDate start = LocalDate.of(2026, 1, 1);
        for (int index = 0; index < 30; index++) {
            prices.add(day(start.plusDays(index), 180, 200, 175, 180, 100));
        }
        for (int index = 0; index < 45; index++) {
            prices.add(day(start.plusDays(30L + index), 103, 110, 100, 105, 100));
        }
        prices.add(day(start.plusDays(75), 106, 121, 106, 120, 500));
        prices.add(day(start.plusDays(76), 118, 122, 114, 117, 120));

        StrategyCandidate withDefault =
                detector.analyze(new MarketRankingItem("005930", "테스트종목", 115, -1.0, 100), prices);
        StrategyCandidate withCustom =
                detector.analyze(
                        new MarketRankingItem("005930", "테스트종목", 115, -1.0, 100), prices, 45);

        assertTrue(withDefault.matchedConditions().contains("분석할 일봉 데이터 부족"));
        assertTrue(withCustom.matchedConditions().contains("45거래일 박스권 횡보"));
        assertTrue(withCustom.matchedConditions().contains("거래량을 동반한 초기 박스 돌파"));
    }

    @Test
    void reportsInsufficientHistory() {
        StrategyCandidate result =
                detector.analyze(
                        new MarketRankingItem("005930", "테스트종목", 100, 0, 0),
                        List.of(day(LocalDate.of(2026, 1, 1), 100, 100, 100, 100, 100)));

        assertEquals(0, result.score());
        assertTrue(result.matchedConditions().contains("분석할 일봉 데이터 부족"));
    }

    private DailyPriceResponse day(
            LocalDate date, long open, long high, long low, long close, long volume) {
        return new DailyPriceResponse(
                date.format(DateTimeFormatter.BASIC_ISO_DATE), open, high, low, close, volume);
    }
}
