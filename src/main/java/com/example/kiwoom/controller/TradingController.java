package com.example.kiwoom.controller;

import com.example.kiwoom.dto.TradingModeStatus;
import com.example.kiwoom.service.TradingModeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trading")
@Tag(name = "Trading Safety", description = "자동매매 실행 모드와 안전장치")
public class TradingController {
    private final TradingModeService modes;

    public TradingController(TradingModeService modes) {
        this.modes = modes;
    }

    @GetMapping("/status")
    @Operation(summary = "현재 자동매매 실행 모드와 실주문 차단 상태")
    public TradingModeStatus status() {
        return modes.status();
    }
}
