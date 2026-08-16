package com.example.kiwoom.controller;

import com.example.kiwoom.dto.PageResponse;
import com.example.kiwoom.dto.PortfolioTrade;
import com.example.kiwoom.dto.PortfolioTradeRequest;
import com.example.kiwoom.service.PortfolioTradeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/portfolio/transactions")
@Tag(name = "Portfolio Transactions", description = "매수·매도 거래 및 실현손익 관리")
public class PortfolioTradeController {
    private final PortfolioTradeService service;

    public PortfolioTradeController(PortfolioTradeService service) {
        this.service = service;
    }

    @GetMapping
    public Mono<PageResponse<PortfolioTrade>> findAll(
            Principal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.findAll(principal.getName(), page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<PortfolioTrade> record(
            Principal principal, @Valid @RequestBody PortfolioTradeRequest request) {
        return service.record(principal.getName(), request);
    }
}
