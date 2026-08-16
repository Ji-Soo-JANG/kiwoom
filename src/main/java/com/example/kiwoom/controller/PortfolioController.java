package com.example.kiwoom.controller;

import com.example.kiwoom.dto.PortfolioPosition;
import com.example.kiwoom.dto.PortfolioValuation;
import com.example.kiwoom.service.PortfolioService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import java.util.List;
import java.security.Principal;

@RestController
@RequestMapping("/api/portfolio")
@Tag(name = "Portfolio", description = "보유 종목과 수익률 관리")
public class PortfolioController {
    private final PortfolioService service;
    public PortfolioController(PortfolioService service) { this.service = service; }

    @GetMapping
    public Flux<PortfolioPosition> findAll(Principal principal) { return service.findAll(principal.getName()); }

    @PutMapping("/{code}")
    public Mono<PortfolioPosition> save(Principal principal, @PathVariable String code, @RequestBody PortfolioPosition request) {
        return service.save(principal.getName(), new PortfolioPosition(code, request.quantity(), request.averagePrice()));
    }

    @DeleteMapping("/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> remove(Principal principal, @PathVariable String code) { return service.remove(principal.getName(), code); }

    @GetMapping("/valuation")
    public Mono<List<PortfolioValuation>> valuate(Principal principal) { return service.valuate(principal.getName()); }
}
