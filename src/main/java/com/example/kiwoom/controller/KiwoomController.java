package com.example.kiwoom.controller;

import com.example.kiwoom.dto.DailyPriceResponse;
import com.example.kiwoom.dto.StockPriceResponse;
import com.example.kiwoom.service.KiwoomApiService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/kiwoom")
public class KiwoomController {

    private final KiwoomApiService kiwoomApiService;

    public KiwoomController(
            KiwoomApiService kiwoomApiService
    ) {
        this.kiwoomApiService = kiwoomApiService;
    }

    /**
     * 한 종목의 현재가 조회
     *
     * 예:
     * GET /api/kiwoom/stock-price/005930
     */
    @GetMapping("/stock-price/{code}")
    public Mono<StockPriceResponse> getStockCurrentPrice(
            @PathVariable String code
    ) {
        return kiwoomApiService
                .getStockCurrentPrice(code);
    }

    /**
     * 여러 종목의 현재가 조회
     *
     * 예:
     * GET /api/kiwoom/stock-prices?codes=005930,000660
     */
    @GetMapping("/stock-prices")
    public Mono<List<StockPriceResponse>> getMultipleStockPrices(
            @RequestParam List<String> codes
    ) {
        return kiwoomApiService
                .getMultipleStockPrices(codes);
    }

    @GetMapping("/stock-price/{code}/daily")
    public Mono<List<DailyPriceResponse>> getDailyPrices(
            @PathVariable String code,
            @RequestParam(required = false) String baseDate
    ) {
        return kiwoomApiService.getDailyPrices(
                code,
                baseDate
        );
    }
}
