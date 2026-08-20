package com.example.kiwoom.controller;

import com.example.kiwoom.dto.MarketDataSyncStatus;
import com.example.kiwoom.service.FullMarketDataCollectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 전체 종목 일봉 일괄 수집(FullMarketDataCollectionService)을 HTTP로 실행하기 위한 관리자 엔드포인트입니다. /api/kiwoom/admin/**
 * 경로는 ADMIN 권한이 필요합니다.
 */
@RestController
@RequestMapping("/api/kiwoom/admin/full-market-data")
@Tag(name = "FullMarketData", description = "전체 종목 일봉 일괄 수집")
public class FullMarketDataController {

    private final FullMarketDataCollectionService collection;

    public FullMarketDataController(FullMarketDataCollectionService collection) {
        this.collection = collection;
    }

    /** 전체 종목 일봉 수집 상태 조회 */
    @GetMapping
    @Operation(summary = "전체 종목 일봉 일괄 수집 상태 조회")
    public Mono<MarketDataSyncStatus> status() {
        return collection.status();
    }

    /** 전체 종목 일봉 수집 시작(실행 중이면 현재 상태 반환) */
    @PostMapping("/sync")
    @Operation(summary = "전체 종목 일봉 일괄 수집 시작")
    public Mono<MarketDataSyncStatus> synchronize() {
        return collection.synchronizeAll();
    }
}
