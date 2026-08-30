package com.example.kiwoom.research.walkforward;

import com.example.kiwoom.dto.DailyPriceResponse;
import com.example.kiwoom.research.backtest.BacktestConfig;
import com.example.kiwoom.research.backtest.EventBacktestEngine;
import com.example.kiwoom.research.backtest.dto.BacktestResponse;
import com.example.kiwoom.research.backtest.dto.BacktestTrade;
import com.example.kiwoom.research.walkforward.dto.WalkForwardFold;
import com.example.kiwoom.research.walkforward.dto.WalkForwardReport;
import com.example.kiwoom.strategy.implementation.CurrentRecoveryPullbackStrategy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

class WalkForwardAnalyzer {
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private final EventBacktestEngine engine = new EventBacktestEngine();

    WalkForwardReport analyze(
            String code,
            String name,
            LocalDate startDate,
            LocalDate endDate,
            List<DailyPriceResponse> source,
            BacktestConfig config,
            int trainingDays,
            int validationDays,
            int stepDays) {
        List<DailyPriceResponse> prices =
                source.stream().sorted(Comparator.comparing(DailyPriceResponse::getDate)).toList();
        List<LocalDate> periodDates =
                prices.stream()
                        .map(day -> LocalDate.parse(day.getDate(), DATE))
                        .filter(date -> !date.isBefore(startDate) && !date.isAfter(endDate))
                        .toList();
        if (periodDates.size() < trainingDays + validationDays) {
            throw new IllegalArgumentException("워크포워드 구간을 만들기에 거래일 데이터가 부족합니다.");
        }

        List<WalkForwardFold> folds = new ArrayList<>();
        List<BacktestTrade> validationTrades = new ArrayList<>();
        int foldNo = 1;
        for (int offset = 0;
                offset + trainingDays + validationDays <= periodDates.size();
                offset += stepDays) {
            LocalDate trainingStart = periodDates.get(offset);
            LocalDate trainingEnd = periodDates.get(offset + trainingDays - 1);
            LocalDate validationStart = periodDates.get(offset + trainingDays);
            LocalDate validationEnd = periodDates.get(offset + trainingDays + validationDays - 1);
            BacktestResponse training =
                    engine.run(code, name, trainingStart, trainingEnd, prices, config);
            BacktestResponse validation =
                    engine.run(code, name, validationStart, validationEnd, prices, config);
            BacktestResponse noCost =
                    engine.run(
                            code,
                            name,
                            validationStart,
                            validationEnd,
                            prices,
                            config.withoutCosts());
            BigDecimal costDrag = noCost.finalCapital().subtract(validation.finalCapital());
            validationTrades.addAll(validation.trades());
            folds.add(
                    new WalkForwardFold(
                            foldNo++,
                            trainingStart,
                            trainingEnd,
                            validationStart,
                            validationEnd,
                            training.tradeCount(),
                            training.totalReturnRate(),
                            validation.tradeCount(),
                            validation.winRate(),
                            validation.expectancy(),
                            validation.totalReturnRate(),
                            validation.maxDrawdownRate(),
                            money(costDrag)));
        }

        BigDecimal totalNet =
                validationTrades.stream()
                        .map(BacktestTrade::netProfitLoss)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expectancy =
                validationTrades.isEmpty()
                        ? money(BigDecimal.ZERO)
                        : money(
                                totalNet.divide(
                                        BigDecimal.valueOf(validationTrades.size()),
                                        8,
                                        RoundingMode.HALF_UP));
        double maxDrawdown =
                folds.stream()
                        .mapToDouble(WalkForwardFold::validationMaxDrawdownRate)
                        .min()
                        .orElse(0);
        double averageReturn =
                folds.stream()
                        .mapToDouble(WalkForwardFold::validationReturnRate)
                        .average()
                        .orElse(0);
        BigDecimal costDrag =
                folds.stream()
                        .map(WalkForwardFold::costDrag)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean passed =
                folds.size() >= 2
                        && validationTrades.size() >= 5
                        && expectancy.signum() > 0
                        && maxDrawdown >= -0.20;
        String verdict =
                passed ? "기본 연구 통과 기준 충족" : "검증 거래 5건 이상, 비용 후 기대값 양수, 최대 낙폭 -20% 이내 조건을 충족하지 못함";
        return new WalkForwardReport(
                null,
                CurrentRecoveryPullbackStrategy.VERSION_KEY,
                code,
                name,
                startDate,
                endDate,
                trainingDays,
                validationDays,
                stepDays,
                folds.size(),
                validationTrades.size(),
                expectancy,
                roundRate(maxDrawdown),
                roundRate(averageReturn),
                money(costDrag),
                passed,
                verdict,
                List.copyOf(folds),
                Instant.now());
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private double roundRate(double value) {
        return Math.round(value * 1_000_000.0) / 1_000_000.0;
    }
}
