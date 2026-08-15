package com.example.kiwoom.controller;

import com.example.kiwoom.dto.DailyPriceResponse;
import com.example.kiwoom.dto.StockPriceResponse;
import com.example.kiwoom.service.KiwoomApiService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/kiwoom")
@Tag(name = "Kiwoom", description = "주식 현재가 및 일봉 조회")
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
    @Operation(summary = "단일 종목 현재가 조회")
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
    @Operation(summary = "여러 종목 현재가 조회")
    public Mono<List<StockPriceResponse>> getMultipleStockPrices(
            @RequestParam List<String> codes
    ) {
        return kiwoomApiService
                .getMultipleStockPrices(codes);
    }

    @GetMapping("/stock-price/{code}/daily")
    @Operation(summary = "종목 일봉 조회")
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
