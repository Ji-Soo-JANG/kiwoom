package com.example.kiwoom.controller;

import com.example.kiwoom.dto.PortfolioPosition;
import com.example.kiwoom.dto.PortfolioValuation;
import com.example.kiwoom.service.PortfolioService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import java.util.List;

@RestController
@RequestMapping("/api/portfolio")
@Tag(name = "Portfolio", description = "보유 종목과 수익률 관리")
public class PortfolioController {
    private final PortfolioService service;
    public PortfolioController(PortfolioService service) { this.service = service; }

    @GetMapping
    public List<PortfolioPosition> findAll() { return service.findAll(); }

    @PutMapping("/{code}")
    public PortfolioPosition save(@PathVariable String code, @RequestBody PortfolioPosition request) {
        return service.save(new PortfolioPosition(code, request.quantity(), request.averagePrice()));
    }

    @DeleteMapping("/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable String code) { service.remove(code); }

    @GetMapping("/valuation")
    public Mono<List<PortfolioValuation>> valuate() { return service.valuate(); }
}
