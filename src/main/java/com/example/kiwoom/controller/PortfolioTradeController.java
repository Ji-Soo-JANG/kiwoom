package com.example.kiwoom.controller;

import com.example.kiwoom.dto.PortfolioTrade;
import com.example.kiwoom.dto.PortfolioTradeRequest;
import com.example.kiwoom.service.PortfolioTradeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/portfolio/transactions")
@Tag(name = "Portfolio Transactions", description = "매수·매도 거래 및 실현손익 관리")
public class PortfolioTradeController {
    private final PortfolioTradeService service;

    public PortfolioTradeController(PortfolioTradeService service) { this.service = service; }

    @GetMapping
    public Flux<PortfolioTrade> findAll() { return service.findAll(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<PortfolioTrade> record(@RequestBody PortfolioTradeRequest request) {
        return service.record(request);
    }
}
