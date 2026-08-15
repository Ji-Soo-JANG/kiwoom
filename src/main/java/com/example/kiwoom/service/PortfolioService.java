package com.example.kiwoom.service;

import com.example.kiwoom.dto.PortfolioPosition;
import com.example.kiwoom.dto.PortfolioValuation;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PortfolioService {
    private final Map<String, PortfolioPosition> positions = new ConcurrentHashMap<>();
    private final KiwoomApiService kiwoomApiService;

    public PortfolioService(KiwoomApiService kiwoomApiService) {
        this.kiwoomApiService = kiwoomApiService;
    }

    public List<PortfolioPosition> findAll() {
        return positions.values().stream()
                .sorted(Comparator.comparing(PortfolioPosition::code)).toList();
    }

    public PortfolioPosition save(PortfolioPosition position) {
        validate(position);
        positions.put(position.code(), position);
        return position;
    }

    public void remove(String code) {
        if (code == null || !code.matches("\\d{6}")) {
            throw new IllegalArgumentException("종목 코드는 6자리 숫자여야 합니다");
        }
        positions.remove(code);
    }

    public Mono<List<PortfolioValuation>> valuate() {
        return Flux.fromIterable(findAll())
                .flatMap(position -> kiwoomApiService.getStockCurrentPrice(position.code())
                        .map(price -> calculate(position, new BigDecimal(price.getCurrentPrice()))), 3)
                .collectSortedList(Comparator.comparing(PortfolioValuation::code));
    }

    PortfolioValuation calculate(PortfolioPosition position, BigDecimal currentPrice) {
        BigDecimal purchase = position.averagePrice().multiply(position.quantity());
        BigDecimal evaluation = currentPrice.multiply(position.quantity());
        BigDecimal profit = evaluation.subtract(purchase);
        BigDecimal rate = profit.multiply(BigDecimal.valueOf(100))
                .divide(purchase, 2, RoundingMode.HALF_UP);
        return new PortfolioValuation(position.code(), position.quantity(), position.averagePrice(),
                currentPrice, purchase, evaluation, profit, rate);
    }

    private void validate(PortfolioPosition position) {
        if (position == null || position.code() == null || !position.code().matches("\\d{6}")) {
            throw new IllegalArgumentException("종목 코드는 6자리 숫자여야 합니다");
        }
        if (position.quantity() == null || position.quantity().signum() <= 0) {
            throw new IllegalArgumentException("보유 수량은 0보다 커야 합니다");
        }
        if (position.averagePrice() == null || position.averagePrice().signum() <= 0) {
            throw new IllegalArgumentException("평균 매입가는 0보다 커야 합니다");
        }
    }
}
