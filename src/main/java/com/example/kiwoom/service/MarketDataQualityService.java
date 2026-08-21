package com.example.kiwoom.service;

import com.example.kiwoom.dto.MarketDataQualityReport;
import com.example.kiwoom.dto.StoredDailyCandle;
import com.example.kiwoom.repository.MarketDataQualityRepository;
import com.example.kiwoom.repository.MarketDataRepository;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class MarketDataQualityService {
    public static final String POLICY_VERSION = "adjusted-price-request-quality-v1";

    private final MarketDataRepository marketData;
    private final MarketDataQualityRepository quality;
    private final MarketDataQualityAnalyzer analyzer;

    public MarketDataQualityService(
            MarketDataRepository marketData,
            MarketDataQualityRepository quality,
            MarketDataQualityAnalyzer analyzer) {
        this.marketData = marketData;
        this.quality = quality;
        this.analyzer = analyzer;
    }

    public Mono<MarketDataQualityReport> inspect() {
        AtomicInteger stocks = new AtomicInteger();
        AtomicLong candles = new AtomicLong();
        return marketData
                .findAllCandlesForQuality()
                .doOnNext(ignored -> candles.incrementAndGet())
                .bufferUntilChanged(StoredDailyCandle::code)
                .doOnNext(ignored -> stocks.incrementAndGet())
                .flatMapIterable(analyzer::analyze)
                .collectList()
                .flatMap(
                        issues ->
                                quality.save(POLICY_VERSION, stocks.get(), candles.get(), issues));
    }

    public Mono<MarketDataQualityReport> latest() {
        return quality.findLatest();
    }
}
