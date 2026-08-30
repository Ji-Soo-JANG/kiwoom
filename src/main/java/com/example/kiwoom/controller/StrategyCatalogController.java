package com.example.kiwoom.controller;

import com.example.kiwoom.strategy.model.StrategyDefinition;
import com.example.kiwoom.strategy.service.StrategyCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/trading/strategies")
public class StrategyCatalogController {
    private final StrategyCatalogService service;

    public StrategyCatalogController(StrategyCatalogService service) {
        this.service = service;
    }

    @GetMapping
    public Flux<StrategyDefinition> strategies() {
        return service.findAll();
    }
}
