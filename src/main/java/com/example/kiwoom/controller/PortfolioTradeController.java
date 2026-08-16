package com.example.kiwoom.controller;

import com.example.kiwoom.dto.PageResponse;
import com.example.kiwoom.dto.PortfolioTrade;
import com.example.kiwoom.dto.PortfolioTradeRequest;
import com.example.kiwoom.service.PortfolioService;
import com.example.kiwoom.service.PortfolioTradeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/portfolio/transactions")
@Tag(name = "Portfolio Transactions", description = "매수·매도 거래 및 실현손익 관리")
public class PortfolioTradeController {
    private final PortfolioTradeService service;
    private final PortfolioService portfolioService;

    public PortfolioTradeController(
            PortfolioTradeService service, PortfolioService portfolioService) {
        this.service = service;
        this.portfolioService = portfolioService;
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

    @GetMapping(value = "/export", produces = "text/csv")
    public Mono<ResponseEntity<String>> exportCsv(Principal principal) {
        return service.exportCsv(principal.getName())
                .map(
                        body ->
                                ResponseEntity.ok()
                                        .header(
                                                "Content-Disposition",
                                                "attachment; filename=portfolio-trades.csv")
                                        .contentType(MediaType.parseMediaType("text/csv"))
                                        .body(body));
    }

    @PostMapping(value = "/import", consumes = "text/csv")
    public Flux<PortfolioTrade> importCsv(Principal principal, @RequestBody String csv) {
        return service.importCsv(principal.getName(), csv);
    }

    @GetMapping("/profit-trend")
    public Mono<java.util.List<com.example.kiwoom.dto.PortfolioProfitPoint>> profitTrend(
            Principal principal) {
        return service.profitTrend(principal.getName(), portfolioService);
    }
}
