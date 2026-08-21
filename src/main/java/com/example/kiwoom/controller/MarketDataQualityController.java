package com.example.kiwoom.controller;

import com.example.kiwoom.dto.MarketDataQualityReport;
import com.example.kiwoom.service.MarketDataQualityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/kiwoom/admin/market-data/quality")
@Tag(name = "MarketDataQuality", description = "저장된 일봉 데이터 품질 검사")
public class MarketDataQualityController {
    private final MarketDataQualityService quality;

    public MarketDataQualityController(MarketDataQualityService quality) {
        this.quality = quality;
    }

    @PostMapping("/inspect")
    @Operation(summary = "전체 저장 일봉 품질 검사 실행")
    public Mono<MarketDataQualityReport> inspect() {
        return quality.inspect();
    }

    @GetMapping
    @Operation(summary = "최근 일봉 품질 검사 결과 조회")
    public Mono<MarketDataQualityReport> latest() {
        return quality.latest();
    }
}
