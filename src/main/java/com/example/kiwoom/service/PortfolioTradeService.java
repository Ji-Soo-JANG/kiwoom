package com.example.kiwoom.service;

import com.example.kiwoom.dto.*;
import com.example.kiwoom.repository.PortfolioRepository;
import com.example.kiwoom.repository.PortfolioTradeRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class PortfolioTradeService {
    private final PortfolioRepository portfolioRepository;
    private final PortfolioTradeRepository tradeRepository;

    public PortfolioTradeService(
            PortfolioRepository portfolioRepository, PortfolioTradeRepository tradeRepository) {
        this.portfolioRepository = portfolioRepository;
        this.tradeRepository = tradeRepository;
    }

    public Mono<PageResponse<PortfolioTrade>> findAll(String username, int page, int size) {
        validatePage(page, size);
        return Mono.zip(
                        tradeRepository.findAll(username, page, size).collectList(),
                        tradeRepository.count(username))
                .map(tuple -> new PageResponse<>(tuple.getT1(), page, size, tuple.getT2()));
    }

    public Mono<String> exportCsv(String username) {
        return tradeRepository
                .findAll(username, 0, 10_000)
                .map(this::csvLine)
                .collectList()
                .map(
                        lines ->
                                "code,type,quantity,price,fee,tax,tradedAt\n"
                                        + String.join("\n", lines));
    }

    public Flux<PortfolioTrade> importCsv(String username, String csv) {
        if (csv == null || csv.isBlank())
            return Flux.error(new IllegalArgumentException("CSV 내용이 비어 있습니다"));
        String[] lines = csv.strip().split("\\R");
        int start = lines[0].toLowerCase().startsWith("code,") ? 1 : 0;
        return Flux.range(start, lines.length - start)
                .concatMap(index -> record(username, parseCsvLine(lines[index], index + 1)));
    }

    public Mono<List<PortfolioProfitPoint>> profitTrend(
            String username, PortfolioService portfolioService) {
        Mono<List<PortfolioTrade>> trades =
                tradeRepository.findAll(username, 0, 10_000).collectList();
        Mono<BigDecimal> unrealized =
                portfolioService
                        .valuate(username)
                        .map(
                                values ->
                                        values.stream()
                                                .map(PortfolioValuation::profitLoss)
                                                .reduce(BigDecimal.ZERO, BigDecimal::add));
        return Mono.zip(trades, unrealized)
                .map(tuple -> buildProfitTrend(tuple.getT1(), tuple.getT2()));
    }

    private List<PortfolioProfitPoint> buildProfitTrend(
            List<PortfolioTrade> trades, BigDecimal unrealized) {
        List<PortfolioTrade> sorted =
                trades.stream().sorted(Comparator.comparing(PortfolioTrade::tradedAt)).toList();
        List<PortfolioProfitPoint> points = new ArrayList<>();
        BigDecimal realized = BigDecimal.ZERO;
        for (PortfolioTrade trade : sorted) {
            realized = realized.add(trade.realizedProfitLoss());
            LocalDate date = trade.tradedAt().toLocalDate();
            if (!points.isEmpty() && points.get(points.size() - 1).date().equals(date))
                points.remove(points.size() - 1);
            points.add(new PortfolioProfitPoint(date, realized, BigDecimal.ZERO, realized));
        }
        if (!points.isEmpty()) {
            PortfolioProfitPoint latest = points.remove(points.size() - 1);
            points.add(
                    new PortfolioProfitPoint(
                            latest.date(),
                            latest.realizedProfitLoss(),
                            unrealized,
                            latest.realizedProfitLoss().add(unrealized)));
        }
        return points;
    }

    private String csvLine(PortfolioTrade trade) {
        return String.join(
                ",",
                trade.code(),
                trade.type().name(),
                trade.quantity().toPlainString(),
                trade.price().toPlainString(),
                trade.fee().toPlainString(),
                trade.tax().toPlainString(),
                trade.tradedAt().toString());
    }

    private PortfolioTradeRequest parseCsvLine(String line, int lineNumber) {
        try {
            String[] values = line.split(",", -1);
            if (values.length < 6) throw new IllegalArgumentException();
            return new PortfolioTradeRequest(
                    values[0],
                    TradeType.valueOf(values[1]),
                    new BigDecimal(values[2]),
                    new BigDecimal(values[3]),
                    new BigDecimal(values[4]),
                    new BigDecimal(values[5]));
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("CSV " + lineNumber + "번째 줄 형식이 올바르지 않습니다");
        }
    }

    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<PortfolioTrade> record(String username, PortfolioTradeRequest request) {
        PortfolioTradeRequest normalized = validate(request);
        return portfolioRepository
                .findByCode(username, normalized.code())
                .map(position -> calculate(normalized, position))
                .switchIfEmpty(Mono.fromSupplier(() -> calculate(normalized, null)))
                .flatMap(result -> persist(username, result));
    }

    private Mono<PortfolioTrade> persist(String username, TradeResult result) {
        Mono<?> positionChange =
                result.position() == null
                        ? portfolioRepository.remove(username, result.trade().code())
                        : portfolioRepository.save(username, result.position());
        return positionChange.then(tradeRepository.save(username, result.trade()));
    }

    private TradeResult calculate(PortfolioTradeRequest request, PortfolioPosition current) {
        BigDecimal zero = BigDecimal.ZERO;
        BigDecimal realized = zero;
        PortfolioPosition next;
        if (request.type() == TradeType.BUY) {
            BigDecimal oldQuantity = current == null ? zero : current.quantity();
            BigDecimal oldCost =
                    current == null ? zero : current.averagePrice().multiply(oldQuantity);
            BigDecimal newQuantity = oldQuantity.add(request.quantity());
            BigDecimal newCost =
                    oldCost.add(request.price().multiply(request.quantity())).add(request.fee());
            BigDecimal average = newCost.divide(newQuantity, 4, RoundingMode.HALF_UP);
            next = new PortfolioPosition(request.code(), newQuantity, average);
        } else {
            if (current == null || current.quantity().compareTo(request.quantity()) < 0) {
                throw new IllegalArgumentException("매도 수량이 보유 수량보다 많습니다");
            }
            realized =
                    request.price()
                            .subtract(current.averagePrice())
                            .multiply(request.quantity())
                            .subtract(request.fee())
                            .subtract(request.tax());
            BigDecimal remaining = current.quantity().subtract(request.quantity());
            next =
                    remaining.signum() == 0
                            ? null
                            : new PortfolioPosition(
                                    request.code(), remaining, current.averagePrice());
        }
        PortfolioTrade trade =
                new PortfolioTrade(
                        null,
                        request.code(),
                        request.type(),
                        request.quantity(),
                        request.price(),
                        request.fee(),
                        request.tax(),
                        realized,
                        OffsetDateTime.now());
        return new TradeResult(next, trade);
    }

    private PortfolioTradeRequest validate(PortfolioTradeRequest request) {
        if (request == null || request.code() == null || !request.code().trim().matches("\\d{6}"))
            throw new IllegalArgumentException("종목 코드는 6자리 숫자여야 합니다");
        if (request.type() == null) throw new IllegalArgumentException("거래 유형은 BUY 또는 SELL이어야 합니다");
        if (request.quantity() == null || request.quantity().signum() <= 0)
            throw new IllegalArgumentException("거래 수량은 0보다 커야 합니다");
        if (request.price() == null || request.price().signum() <= 0)
            throw new IllegalArgumentException("거래 가격은 0보다 커야 합니다");
        BigDecimal fee = request.fee() == null ? BigDecimal.ZERO : request.fee();
        BigDecimal tax = request.tax() == null ? BigDecimal.ZERO : request.tax();
        if (fee.signum() < 0 || tax.signum() < 0)
            throw new IllegalArgumentException("수수료와 세금은 0 이상이어야 합니다");
        return new PortfolioTradeRequest(
                request.code().trim(),
                request.type(),
                request.quantity(),
                request.price(),
                fee,
                tax);
    }

    private void validatePage(int page, int size) {
        if (page < 0) throw new IllegalArgumentException("페이지 번호는 0 이상이어야 합니다");
        if (size < 1 || size > 100) throw new IllegalArgumentException("페이지 크기는 1부터 100 사이여야 합니다");
    }

    private record TradeResult(PortfolioPosition position, PortfolioTrade trade) {}
}
