package com.example.kiwoom.controller;

import com.example.kiwoom.dto.OrderReconciliationReport;
import com.example.kiwoom.dto.PaperAccountStatus;
import com.example.kiwoom.dto.PaperOrderRequest;
import com.example.kiwoom.dto.PaperPosition;
import com.example.kiwoom.dto.TradingModeStatus;
import com.example.kiwoom.dto.TradingOrder;
import com.example.kiwoom.service.PaperOrderService;
import com.example.kiwoom.service.TradingModeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/trading")
@Tag(name = "Trading Safety", description = "자동매매 실행 모드와 안전장치")
public class TradingController {
    private final TradingModeService modes;
    private final PaperOrderService orders;

    public TradingController(TradingModeService modes, PaperOrderService orders) {
        this.modes = modes;
        this.orders = orders;
    }

    @GetMapping("/status")
    @Operation(summary = "현재 자동매매 실행 모드와 실주문 차단 상태")
    public TradingModeStatus status() {
        return modes.status();
    }

    @PostMapping("/orders")
    @Operation(summary = "고유 의사결정 ID로 PAPER 주문 생성")
    public Mono<TradingOrder> place(@Valid @RequestBody PaperOrderRequest request) {
        return orders.place(request);
    }

    @GetMapping("/orders")
    public Flux<TradingOrder> orders() {
        return orders.findAll();
    }

    @PostMapping("/orders/{id}/cancel")
    public Mono<TradingOrder> cancel(@PathVariable long id) {
        return orders.cancel(id);
    }

    @GetMapping("/paper/account")
    public Mono<PaperAccountStatus> account() {
        return orders.account();
    }

    @GetMapping("/paper/positions")
    public Flux<PaperPosition> positions() {
        return orders.positions();
    }

    @PostMapping("/reconcile")
    @Operation(summary = "PAPER 주문·체결·잔고·현금 대사")
    public Mono<OrderReconciliationReport> reconcile() {
        return orders.reconcile();
    }
}
