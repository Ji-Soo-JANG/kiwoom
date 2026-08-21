package com.example.kiwoom.service;

import com.example.kiwoom.dto.WalkForwardReport;
import com.example.kiwoom.dto.WalkForwardRequest;
import com.example.kiwoom.error.ResourceNotFoundException;
import com.example.kiwoom.repository.MarketDataRepository;
import com.example.kiwoom.repository.WalkForwardRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class WalkForwardService {
    private final MarketDataRepository marketData;
    private final WalkForwardRepository reports;
    private final WalkForwardAnalyzer analyzer = new WalkForwardAnalyzer();

    public WalkForwardService(MarketDataRepository marketData, WalkForwardRepository reports) {
        this.marketData = marketData;
        this.reports = reports;
    }

    public Mono<WalkForwardReport> run(WalkForwardRequest request) {
        var backtest = request.backtest();
        if (backtest.startDate().isAfter(backtest.endDate())) {
            return Mono.error(new IllegalArgumentException("시작일은 종료일보다 늦을 수 없습니다."));
        }
        int trainingDays = request.trainingDays() == null ? 240 : request.trainingDays();
        int validationDays = request.validationDays() == null ? 60 : request.validationDays();
        int stepDays = request.stepDays() == null ? validationDays : request.stepDays();
        if (stepDays < validationDays) {
            return Mono.error(
                    new IllegalArgumentException("검증 거래가 중복 집계되지 않도록 이동 기간은 검증 기간 이상이어야 합니다."));
        }
        BacktestConfig config = BacktestConfig.from(backtest);
        if (config.positionSizeRate() > 1
                || config.feeRate() > 0.1
                || config.taxRate() > 0.1
                || config.slippageRate() > 0.1
                || config.stopLossRate() >= 1) {
            return Mono.error(new IllegalArgumentException("워크포워드 비율 설정 범위가 올바르지 않습니다."));
        }
        return marketData
                .findStocksByCodes(java.util.List.of(backtest.code()))
                .next()
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("저장된 종목 정보를 찾을 수 없습니다.")))
                .flatMap(
                        stock ->
                                marketData
                                        .findDailyPrices(backtest.code(), 3000, backtest.endDate())
                                        .collectList()
                                        .map(
                                                prices ->
                                                        analyzer.analyze(
                                                                stock.code(),
                                                                stock.name(),
                                                                backtest.startDate(),
                                                                backtest.endDate(),
                                                                prices,
                                                                config,
                                                                trainingDays,
                                                                validationDays,
                                                                stepDays)))
                .flatMap(reports::save);
    }
}
