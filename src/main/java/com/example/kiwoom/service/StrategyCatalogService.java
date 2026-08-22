package com.example.kiwoom.service;

import com.example.kiwoom.dto.StrategyDefinition;
import com.example.kiwoom.repository.StrategyDefinitionRepository;
import com.example.kiwoom.service.strategy.StrategyRegistry;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class StrategyCatalogService {
    private final StrategyDefinitionRepository repository;
    private final StrategyRegistry registry;

    public StrategyCatalogService(
            StrategyDefinitionRepository repository, StrategyRegistry registry) {
        this.repository = repository;
        this.registry = registry;
    }

    public Flux<StrategyDefinition> findAll() {
        return repository
                .findAll()
                .filter(definition -> registry.versionKeys().contains(definition.versionKey()));
    }
}
