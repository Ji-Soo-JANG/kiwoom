package com.example.kiwoom.controller;

import com.example.kiwoom.dto.LimitedTradeCandidate;
import com.example.kiwoom.dto.PerformanceSampleRequest;
import com.example.kiwoom.dto.TradeApprovalRequest;
import com.example.kiwoom.dto.TradeCandidateRequest;
import com.example.kiwoom.dto.TradingPerformanceStatus;
import com.example.kiwoom.service.LimitedTradingService;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/trading/limited")
public class LimitedTradingController {
    private final LimitedTradingService service;

    public LimitedTradingController(LimitedTradingService service) {
        this.service = service;
    }

    @PostMapping("/candidates")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<LimitedTradeCandidate> create(@Valid @RequestBody TradeCandidateRequest request) {
        return service.create(request);
    }

    @GetMapping("/candidates")
    public Flux<LimitedTradeCandidate> candidates() {
        return service.findAll();
    }

    @PostMapping("/candidates/{id}/approve")
    public Mono<LimitedTradeCandidate> approve(
            @PathVariable long id,
            @Valid @RequestBody TradeApprovalRequest request,
            Principal principal) {
        return service.approve(id, request.confirmation(), principal.getName());
    }

    @PostMapping("/candidates/{id}/reject")
    public Mono<LimitedTradeCandidate> reject(@PathVariable long id) {
        return service.reject(id);
    }

    @PostMapping("/performance")
    public Mono<TradingPerformanceStatus> performance(
            @Valid @RequestBody PerformanceSampleRequest request) {
        return service.recordPerformance(request);
    }

    @GetMapping("/performance")
    public Mono<TradingPerformanceStatus> performance() {
        return service.performance();
    }
}
