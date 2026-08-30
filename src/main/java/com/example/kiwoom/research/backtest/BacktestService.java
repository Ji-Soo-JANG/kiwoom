package com.example.kiwoom.research.backtest;

import com.example.kiwoom.error.ResourceNotFoundException;
import com.example.kiwoom.repository.MarketDataRepository;
import com.example.kiwoom.research.backtest.dto.BacktestRequest;
import com.example.kiwoom.research.backtest.dto.BacktestResponse;
import com.example.kiwoom.research.backtest.repository.BacktestRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class BacktestService {
    private final MarketDataRepository marketData;
    private final BacktestRepository backtests;
    private final EventBacktestEngine engine = new EventBacktestEngine();

    public BacktestService(MarketDataRepository marketData, BacktestRepository backtests) {
        this.marketData = marketData;
        this.backtests = backtests;
    }

    public Mono<BacktestResponse> run(BacktestRequest request) {
        if (request.startDate().isAfter(request.endDate())) {
            return Mono.error(new IllegalArgumentException("시작일은 종료일보다 늦을 수 없습니다."));
        }
        BacktestConfig config = BacktestConfig.from(request);
        if (config.positionSizeRate() > 1
                || config.feeRate() > 0.1
                || config.taxRate() > 0.1
                || config.slippageRate() > 0.1
                || config.stopLossRate() >= 1) {
            return Mono.error(new IllegalArgumentException("백테스트 비율 설정 범위가 올바르지 않습니다."));
        }
        return marketData
                .findStocksByCodes(java.util.List.of(request.code()))
                .next()
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("저장된 종목 정보를 찾을 수 없습니다.")))
                .flatMap(
                        stock ->
                                marketData
                                        .findDailyPrices(request.code(), 2000, request.endDate())
                                        .collectList()
                                        .filter(prices -> !prices.isEmpty())
                                        .switchIfEmpty(
                                                Mono.error(
                                                        new IllegalArgumentException(
                                                                "백테스트할 일봉 데이터가 없습니다.")))
                                        .map(
                                                prices ->
                                                        engine.run(
                                                                stock.code(),
                                                                stock.name(),
                                                                request.startDate(),
                                                                request.endDate(),
                                                                prices,
                                                                config)))
                .flatMap(backtests::save);
    }
}
