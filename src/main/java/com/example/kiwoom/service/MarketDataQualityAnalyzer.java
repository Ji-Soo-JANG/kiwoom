package com.example.kiwoom.service;

import com.example.kiwoom.dto.MarketDataQualityIssue;
import com.example.kiwoom.dto.StoredDailyCandle;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MarketDataQualityAnalyzer {
    private static final double CORPORATE_ACTION_GAP = 0.35;

    private final MarketCalendarService calendar;

    public MarketDataQualityAnalyzer(MarketCalendarService calendar) {
        this.calendar = calendar;
    }

    public List<MarketDataQualityIssue> analyze(List<StoredDailyCandle> source) {
        List<StoredDailyCandle> candles =
                source.stream().sorted(Comparator.comparing(StoredDailyCandle::tradeDate)).toList();
        List<MarketDataQualityIssue> issues = new ArrayList<>();
        StoredDailyCandle previous = null;
        for (StoredDailyCandle candle : candles) {
            validateCandle(candle, issues);
            if (previous != null) validateSequence(previous, candle, issues);
            previous = candle;
        }
        return issues;
    }

    private void validateCandle(StoredDailyCandle candle, List<MarketDataQualityIssue> issues) {
        long maximum = Math.max(candle.openPrice(), candle.closePrice());
        long minimum = Math.min(candle.openPrice(), candle.closePrice());
        if (candle.openPrice() <= 0
                || candle.highPrice() <= 0
                || candle.lowPrice() <= 0
                || candle.closePrice() <= 0
                || candle.highPrice() < maximum
                || candle.lowPrice() > minimum
                || candle.highPrice() < candle.lowPrice()) {
            issues.add(issue(candle, "INVALID_OHLC", "BLOCKING", "가격이 0 이하이거나 OHLC 범위가 맞지 않습니다."));
        }
        if (candle.volume() < 0) {
            issues.add(issue(candle, "NEGATIVE_VOLUME", "BLOCKING", "거래량이 음수입니다."));
        } else if (candle.volume() == 0) {
            issues.add(issue(candle, "ZERO_VOLUME", "WARNING", "거래량이 0입니다."));
        }
        if (!calendar.isTradingDay(candle.tradeDate())) {
            issues.add(issue(candle, "NON_TRADING_DAY", "WARNING", "휴장일로 설정된 날짜에 일봉이 있습니다."));
        }
    }

    private void validateSequence(
            StoredDailyCandle previous,
            StoredDailyCandle current,
            List<MarketDataQualityIssue> issues) {
        if (previous.closePrice() > 0) {
            double change = (double) current.closePrice() / previous.closePrice() - 1;
            if (Math.abs(change) > CORPORATE_ACTION_GAP) {
                issues.add(
                        issue(
                                current,
                                "CORPORATE_ACTION_UNVERIFIED",
                                "BLOCKING",
                                "수정주가 요청 후에도 전일 대비 "
                                        + Math.round(change * 10_000.0) / 100.0
                                        + "% 가격 단절이 있어 기업행사 확인이 필요합니다."));
            }
        }
        int missing = 0;
        LocalDate date = previous.tradeDate().plusDays(1);
        while (date.isBefore(current.tradeDate())) {
            if (calendar.isTradingDay(date)) missing++;
            date = date.plusDays(1);
        }
        if (missing >= 3) {
            issues.add(
                    issue(
                            current,
                            "MISSING_TRADING_DAYS",
                            "WARNING",
                            "두 일봉 사이에 예상 거래일 " + missing + "일이 누락되었습니다."));
        }
    }

    private MarketDataQualityIssue issue(
            StoredDailyCandle candle, String type, String severity, String detail) {
        return new MarketDataQualityIssue(
                candle.code(), candle.tradeDate(), type, severity, detail);
    }
}
