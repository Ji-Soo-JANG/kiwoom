package com.example.kiwoom.service;

import com.example.kiwoom.dto.*;
import com.example.kiwoom.repository.PortfolioRepository;
import com.example.kiwoom.repository.PortfolioTradeRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
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

    public Flux<PortfolioTrade> findAll(String username) {
        return tradeRepository.findAll(username);
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

    private record TradeResult(PortfolioPosition position, PortfolioTrade trade) {}
}
