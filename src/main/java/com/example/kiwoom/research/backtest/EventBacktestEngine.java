package com.example.kiwoom.research.backtest;

import com.example.kiwoom.dto.DailyPriceResponse;
import com.example.kiwoom.dto.MarketRankingItem;
import com.example.kiwoom.research.backtest.dto.BacktestResponse;
import com.example.kiwoom.research.backtest.dto.BacktestTrade;
import com.example.kiwoom.strategy.implementation.CurrentRecoveryPullbackStrategy;
import com.example.kiwoom.strategy.service.StrategyPatternDetector;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class EventBacktestEngine {
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private final StrategyPatternDetector detector = new StrategyPatternDetector();

    public BacktestResponse run(
            String code,
            String name,
            LocalDate startDate,
            LocalDate endDate,
            List<DailyPriceResponse> source,
            BacktestConfig config) {
        List<DailyPriceResponse> prices =
                source.stream().sorted(Comparator.comparing(DailyPriceResponse::getDate)).toList();
        BigDecimal cash = config.initialCapital();
        BigDecimal peakEquity = cash;
        double maxDrawdown = 0;
        Position position = null;
        boolean enterNextDay = false;
        List<BacktestTrade> trades = new ArrayList<>();

        for (int index = 0; index < prices.size(); index++) {
            DailyPriceResponse day = prices.get(index);
            LocalDate date = LocalDate.parse(day.getDate(), DATE);
            if (date.isAfter(endDate)) break;

            if (position == null && enterNextDay && !date.isBefore(startDate)) {
                position = enter(day, date, cash, config);
                if (position != null) cash = cash.subtract(position.entryCashOut());
                enterNextDay = false;
            }

            if (position != null && date.isAfter(position.entryDate())) {
                Exit exit = exit(day, date, position, config, false);
                if (exit != null) {
                    BacktestTrade trade = close(position, exit, config);
                    trades.add(trade);
                    cash = cash.add(exitCashIn(position, exit, config));
                    position = null;
                }
            }

            BigDecimal equity =
                    position == null
                            ? cash
                            : cash.add(
                                    BigDecimal.valueOf(day.getClosePrice())
                                            .multiply(BigDecimal.valueOf(position.quantity())));
            if (equity.compareTo(peakEquity) > 0) peakEquity = equity;
            if (peakEquity.signum() > 0) {
                double drawdown =
                        equity.divide(peakEquity, 10, RoundingMode.HALF_UP).doubleValue() - 1;
                maxDrawdown = Math.min(maxDrawdown, drawdown);
            }

            if (position == null && !date.isBefore(startDate) && index + 1 < prices.size()) {
                var stock =
                        new MarketRankingItem(code, name, day.getClosePrice(), 0, day.getVolume());
                enterNextDay =
                        detector.analyze(stock, prices.subList(0, index + 1), config.boxRangeDays())
                                .qualified();
            }
        }

        if (position != null) {
            DailyPriceResponse last =
                    prices.stream()
                            .filter(day -> !LocalDate.parse(day.getDate(), DATE).isAfter(endDate))
                            .reduce((first, second) -> second)
                            .orElseThrow();
            Exit exit = exit(last, LocalDate.parse(last.getDate(), DATE), position, config, true);
            BacktestTrade trade = close(position, exit, config);
            trades.add(trade);
            cash = cash.add(exitCashIn(position, exit, config));
        }

        BigDecimal totalNet =
                trades.stream()
                        .map(BacktestTrade::netProfitLoss)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        long wins = trades.stream().filter(trade -> trade.netProfitLoss().signum() > 0).count();
        double totalReturn =
                cash.divide(config.initialCapital(), 10, RoundingMode.HALF_UP).doubleValue() - 1;
        return new BacktestResponse(
                null,
                CurrentRecoveryPullbackStrategy.VERSION_KEY,
                code,
                name,
                startDate,
                endDate,
                money(config.initialCapital()),
                money(cash),
                config.feeRate(),
                config.taxRate(),
                config.slippageRate(),
                trades.size(),
                trades.isEmpty() ? 0 : roundRate((double) wins / trades.size()),
                roundRate(totalReturn),
                roundRate(maxDrawdown),
                trades.isEmpty()
                        ? BigDecimal.ZERO.setScale(4)
                        : money(
                                totalNet.divide(
                                        BigDecimal.valueOf(trades.size()),
                                        8,
                                        RoundingMode.HALF_UP)),
                List.copyOf(trades),
                Instant.now());
    }

    private Position enter(
            DailyPriceResponse day, LocalDate date, BigDecimal cash, BacktestConfig config) {
        BigDecimal rawPrice = BigDecimal.valueOf(day.getOpenPrice());
        BigDecimal fillPrice = rawPrice.multiply(BigDecimal.valueOf(1 + config.slippageRate()));
        BigDecimal budget = cash.multiply(BigDecimal.valueOf(config.positionSizeRate()));
        BigDecimal costPerShare = fillPrice.multiply(BigDecimal.valueOf(1 + config.feeRate()));
        long quantity = budget.divide(costPerShare, 0, RoundingMode.DOWN).longValue();
        if (quantity < 1) return null;
        BigDecimal notional = fillPrice.multiply(BigDecimal.valueOf(quantity));
        BigDecimal fee = notional.multiply(BigDecimal.valueOf(config.feeRate()));
        return new Position(date, fillPrice, rawPrice, quantity, money(notional.add(fee)), 0);
    }

    private Exit exit(
            DailyPriceResponse day,
            LocalDate date,
            Position position,
            BacktestConfig config,
            boolean finalDay) {
        BigDecimal stop =
                position.entryPrice().multiply(BigDecimal.valueOf(1 - config.stopLossRate()));
        BigDecimal take =
                position.entryPrice().multiply(BigDecimal.valueOf(1 + config.takeProfitRate()));
        if (day.getLowPrice() <= stop.doubleValue()) return new Exit(date, stop, "STOP_LOSS");
        if (day.getHighPrice() >= take.doubleValue()) return new Exit(date, take, "TAKE_PROFIT");
        if (position.holdingDays() + 1 >= config.maxHoldingDays())
            return new Exit(date, BigDecimal.valueOf(day.getClosePrice()), "MAX_HOLDING");
        if (finalDay) return new Exit(date, BigDecimal.valueOf(day.getClosePrice()), "END_OF_TEST");
        position.incrementHoldingDays();
        return null;
    }

    private BacktestTrade close(Position position, Exit exit, BacktestConfig config) {
        BigDecimal fillPrice =
                exit.rawPrice().multiply(BigDecimal.valueOf(1 - config.slippageRate()));
        BigDecimal quantity = BigDecimal.valueOf(position.quantity());
        BigDecimal gross = fillPrice.subtract(position.entryPrice()).multiply(quantity);
        BigDecimal entryFee =
                position.entryPrice()
                        .multiply(quantity)
                        .multiply(BigDecimal.valueOf(config.feeRate()));
        BigDecimal exitNotional = fillPrice.multiply(quantity);
        BigDecimal exitFee = exitNotional.multiply(BigDecimal.valueOf(config.feeRate()));
        BigDecimal tax = exitNotional.multiply(BigDecimal.valueOf(config.taxRate()));
        BigDecimal fees = entryFee.add(exitFee);
        BigDecimal net = gross.subtract(fees).subtract(tax);
        BigDecimal rawRoundTrip =
                exit.rawPrice().subtract(position.rawEntryPrice()).multiply(quantity);
        BigDecimal slippage = rawRoundTrip.subtract(gross);
        double returnRate =
                net.divide(position.entryPrice().multiply(quantity), 10, RoundingMode.HALF_UP)
                        .doubleValue();
        return new BacktestTrade(
                position.entryDate(),
                exit.date(),
                money(position.entryPrice()),
                money(fillPrice),
                position.quantity(),
                money(gross),
                money(fees),
                money(tax),
                money(slippage),
                money(net),
                roundRate(returnRate),
                exit.reason());
    }

    private BigDecimal exitCashIn(Position position, Exit exit, BacktestConfig config) {
        BigDecimal fill = exit.rawPrice().multiply(BigDecimal.valueOf(1 - config.slippageRate()));
        BigDecimal proceeds = fill.multiply(BigDecimal.valueOf(position.quantity()));
        return money(
                proceeds.subtract(proceeds.multiply(BigDecimal.valueOf(config.feeRate())))
                        .subtract(proceeds.multiply(BigDecimal.valueOf(config.taxRate()))));
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private double roundRate(double value) {
        return Math.round(value * 1_000_000.0) / 1_000_000.0;
    }

    private static final class Position {
        private final LocalDate entryDate;
        private final BigDecimal entryPrice;
        private final BigDecimal rawEntryPrice;
        private final long quantity;
        private final BigDecimal entryCashOut;
        private int holdingDays;

        private Position(
                LocalDate entryDate,
                BigDecimal entryPrice,
                BigDecimal rawEntryPrice,
                long quantity,
                BigDecimal entryCashOut,
                int holdingDays) {
            this.entryDate = entryDate;
            this.entryPrice = entryPrice;
            this.rawEntryPrice = rawEntryPrice;
            this.quantity = quantity;
            this.entryCashOut = entryCashOut;
            this.holdingDays = holdingDays;
        }

        LocalDate entryDate() {
            return entryDate;
        }

        BigDecimal entryPrice() {
            return entryPrice;
        }

        BigDecimal rawEntryPrice() {
            return rawEntryPrice;
        }

        long quantity() {
            return quantity;
        }

        BigDecimal entryCashOut() {
            return entryCashOut;
        }

        int holdingDays() {
            return holdingDays;
        }

        void incrementHoldingDays() {
            holdingDays++;
        }
    }

    private record Exit(LocalDate date, BigDecimal rawPrice, String reason) {}
}
