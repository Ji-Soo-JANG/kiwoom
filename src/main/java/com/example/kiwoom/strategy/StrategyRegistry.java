package com.example.kiwoom.strategy;

import com.example.kiwoom.error.ResourceNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class StrategyRegistry {
    private final Map<String, StockStrategy> strategies;

    public StrategyRegistry(List<StockStrategy> strategies) {
        this.strategies =
                strategies.stream()
                        .collect(
                                Collectors.toUnmodifiableMap(
                                        StockStrategy::versionKey, Function.identity()));
    }

    public StockStrategy require(String versionKey) {
        StockStrategy strategy = strategies.get(versionKey);
        if (strategy == null)
            throw new ResourceNotFoundException("전략 구현을 찾을 수 없습니다: " + versionKey);
        return strategy;
    }

    public List<String> versionKeys() {
        return strategies.keySet().stream().sorted().toList();
    }
}
