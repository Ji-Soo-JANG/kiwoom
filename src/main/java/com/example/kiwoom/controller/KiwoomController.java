package com.example.kiwoom.controller;

import com.example.kiwoom.dto.AccountPortfolioResponse;
import com.example.kiwoom.dto.BacktestRequest;
import com.example.kiwoom.dto.BacktestResponse;
import com.example.kiwoom.dto.DailyPriceResponse;
import com.example.kiwoom.dto.MarketDataSyncStatus;
import com.example.kiwoom.dto.MarketRankingsResponse;
import com.example.kiwoom.dto.StockPriceResponse;
import com.example.kiwoom.dto.StockSearchResult;
import com.example.kiwoom.dto.StrategyScanResponse;
import com.example.kiwoom.service.BacktestService;
import com.example.kiwoom.service.KiwoomApiService;
import com.example.kiwoom.service.MarketDataCollectionService;
import com.example.kiwoom.service.StrategyScanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/kiwoom")
@Tag(name = "Kiwoom", description = "주식 현재가 및 일봉 조회")
public class KiwoomController {

    private final KiwoomApiService kiwoomApiService;
    private final MarketDataCollectionService marketDataCollectionService;
    private final StrategyScanService strategyScanService;
    private final BacktestService backtestService;

    public KiwoomController(
            KiwoomApiService kiwoomApiService,
            MarketDataCollectionService marketDataCollectionService,
            StrategyScanService strategyScanService,
            BacktestService backtestService) {
        this.kiwoomApiService = kiwoomApiService;
        this.marketDataCollectionService = marketDataCollectionService;
        this.strategyScanService = strategyScanService;
        this.backtestService = backtestService;
    }

    /**
     * 한 종목의 현재가 조회
     *
     * <p>예: GET /api/kiwoom/stock-price/005930
     */
    @GetMapping("/stock-price/{code}")
    @Operation(summary = "단일 종목 현재가 조회")
    public Mono<StockPriceResponse> getStockCurrentPrice(@PathVariable String code) {
        return kiwoomApiService.getStockCurrentPrice(code);
    }

    /**
     * 여러 종목의 현재가 조회
     *
     * <p>예: GET /api/kiwoom/stock-prices?codes=005930,000660
     */
    @GetMapping("/stock-prices")
    @Operation(summary = "여러 종목 현재가 조회")
    public Mono<List<StockPriceResponse>> getMultipleStockPrices(@RequestParam List<String> codes) {
        return kiwoomApiService.getMultipleStockPrices(codes);
    }

    @GetMapping("/stock-price/{code}/daily")
    @Operation(summary = "종목 기간별 차트 조회(일봉·주봉·월봉·년봉)")
    public Mono<List<DailyPriceResponse>> getDailyPrices(
            @PathVariable String code,
            @RequestParam(required = false) String baseDate,
            @RequestParam(defaultValue = "120") int limit,
            @RequestParam(defaultValue = "day") String period) {
        return kiwoomApiService.getPeriodPrices(code, baseDate, limit, period);
    }

    @GetMapping("/stocks/search")
    @Operation(summary = "종목 코드·이름·초성·상품유형 통합 검색")
    public Mono<List<StockSearchResult>> searchStocks(
            @RequestParam String q,
            @RequestParam(required = false, defaultValue = "ALL") String market,
            @RequestParam(required = false, defaultValue = "ALL") String productType) {
        return kiwoomApiService.searchStocks(q, market, productType);
    }

    @GetMapping("/market-rankings")
    @Operation(summary = "급등·급락·거래량 상위 종목 조회")
    public Mono<MarketRankingsResponse> marketRankings(
            @RequestParam(defaultValue = "ALL") String market) {
        return kiwoomApiService.getMarketRankings(market);
    }

    @GetMapping("/strategy-candidates")
    @Operation(summary = "급락·횡보·거래량·돌파·눌림목 전략 후보 조회")
    public Mono<StrategyScanResponse> strategyCandidates(
            @RequestParam(defaultValue = "60") int boxRangeDays,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate asOf) {
        return strategyScanService.scan(boxRangeDays, asOf);
    }

    @GetMapping("/strategy-scans/latest")
    @Operation(summary = "가장 최근에 저장된 전략 후보 스냅샷 조회")
    public Mono<StrategyScanResponse> latestStrategySnapshot() {
        return strategyScanService.latestSnapshot();
    }

    @PostMapping("/backtests")
    @Operation(summary = "수수료·세금·슬리피지를 반영한 단일 종목 이벤트 백테스트")
    public Mono<BacktestResponse> runBacktest(@Valid @RequestBody BacktestRequest request) {
        return backtestService.run(request);
    }

    @GetMapping("/admin/market-data")
    @Operation(summary = "저장된 종목·일봉 데이터 수집 상태 조회")
    public Mono<MarketDataSyncStatus> marketDataStatus() {
        return marketDataCollectionService.status();
    }

    @PostMapping("/admin/market-data/sync")
    @Operation(summary = "종목 목록 저장 및 일봉 증분 수집")
    public Mono<MarketDataSyncStatus> synchronizeMarketData(
            @RequestParam(defaultValue = "20") int limit) {
        return marketDataCollectionService.synchronize(limit);
    }

    @GetMapping("/account/portfolio")
    @Operation(summary = "키움 계좌 평가잔고 조회")
    public Mono<AccountPortfolioResponse> accountPortfolio() {
        return kiwoomApiService.getAccountPortfolio();
    }

    @GetMapping("/admin/stock-catalog")
    public KiwoomApiService.StockCatalogStatus stockCatalogStatus() {
        return kiwoomApiService.stockCatalogStatus();
    }

    @PostMapping("/admin/stock-catalog/refresh")
    public Mono<KiwoomApiService.StockCatalogStatus> refreshStockCatalog() {
        return kiwoomApiService.refreshStockCatalog();
    }
}
