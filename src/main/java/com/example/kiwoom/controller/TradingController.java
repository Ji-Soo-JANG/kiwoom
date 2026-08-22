package com.example.kiwoom.controller;

import com.example.kiwoom.dto.AutoTradingControl;
import com.example.kiwoom.dto.AutoTradingControlRequest;
import com.example.kiwoom.dto.KillSwitchRequest;
import com.example.kiwoom.dto.KillSwitchResumeRequest;
import com.example.kiwoom.dto.OrderReconciliationReport;
import com.example.kiwoom.dto.PaperAccountStatus;
import com.example.kiwoom.dto.PaperBrokerVerificationReport;
import com.example.kiwoom.dto.PaperOrderRequest;
import com.example.kiwoom.dto.PaperPosition;
import com.example.kiwoom.dto.PaperRiskStatus;
import com.example.kiwoom.dto.TradingModeStatus;
import com.example.kiwoom.dto.TradingOrder;
import com.example.kiwoom.service.AutoTradingControlService;
import com.example.kiwoom.service.PaperBrokerVerificationService;
import com.example.kiwoom.service.PaperOrderService;
import com.example.kiwoom.service.PaperRiskService;
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
    private final PaperRiskService risks;
    private final PaperBrokerVerificationService verification;
    private final AutoTradingControlService autoTrading;

    public TradingController(
            TradingModeService modes,
            PaperOrderService orders,
            PaperRiskService risks,
            PaperBrokerVerificationService verification,
            AutoTradingControlService autoTrading) {
        this.modes = modes;
        this.orders = orders;
        this.risks = risks;
        this.verification = verification;
        this.autoTrading = autoTrading;
    }

    @PostMapping("/paper/verification")
    @Operation(summary = "외부 주문 없이 부분체결·미체결·정정·취소·복구 시나리오 검증")
    public PaperBrokerVerificationReport verifyPaperLifecycle() {
        return verification.verify();
    }

    @GetMapping("/status")
    @Operation(summary = "현재 자동매매 실행 모드와 실주문 차단 상태")
    public TradingModeStatus status() {
        return modes.status();
    }

    @GetMapping("/automation")
    @Operation(summary = "모의·실투자 자동매매 ON/OFF와 선택 전략 조회")
    public Mono<AutoTradingControl> automation() {
        return autoTrading.get();
    }

    @PostMapping("/automation")
    @Operation(summary = "모의·실투자 자동매매 설정 변경")
    public Mono<AutoTradingControl> updateAutomation(
            @Valid @RequestBody AutoTradingControlRequest request,
            java.security.Principal principal) {
        return autoTrading.update(request, principal.getName());
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

    @GetMapping("/risk")
    @Operation(summary = "PAPER 계좌 위험 한도와 킬 스위치 상태")
    public Mono<PaperRiskStatus> risk() {
        return risks.status();
    }

    @PostMapping("/kill-switch")
    @Operation(summary = "킬 스위치 수동 활성화 및 진행 주문 취소")
    public Mono<PaperRiskStatus> activateKillSwitch(@Valid @RequestBody KillSwitchRequest request) {
        return risks.activate(request.reason());
    }

    @PostMapping("/kill-switch/resume")
    @Operation(summary = "확인 문구를 사용한 킬 스위치 수동 재개")
    public Mono<PaperRiskStatus> resumeKillSwitch(
            @Valid @RequestBody KillSwitchResumeRequest request) {
        return risks.resume(request);
    }
}
