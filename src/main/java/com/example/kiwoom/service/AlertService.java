package com.example.kiwoom.service;

import com.example.kiwoom.dto.*;
import com.example.kiwoom.error.ResourceNotFoundException;
import com.example.kiwoom.repository.AlertRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@Service
public class AlertService {
    private final AlertRepository repository;
    private final KiwoomApiService kiwoomApiService;

    public AlertService(AlertRepository repository, KiwoomApiService kiwoomApiService) {
        this.repository = repository;
        this.kiwoomApiService = kiwoomApiService;
    }

    public Flux<AlertRule> findRules(String username) { return repository.findRules(username); }

    public Mono<AlertRule> addRule(String username, AlertRuleRequest request) {
        AlertRuleRequest validated = validate(request);
        return repository.addRule(username, validated);
    }

    public Mono<AlertRule> updateRule(String username, long id, AlertRuleUpdateRequest request) {
        return repository.findRule(username, id)
                .switchIfEmpty(notFound("알림 규칙을 찾을 수 없습니다"))
                .flatMap(current -> {
                    BigDecimal threshold = request == null || request.threshold() == null
                            ? current.threshold() : request.threshold();
                    boolean enabled = request == null || request.enabled() == null
                            ? current.enabled() : request.enabled();
                    if (threshold.signum() <= 0) return Mono.error(new IllegalArgumentException("목표가는 0보다 커야 합니다"));
                    return repository.updateRule(username, id, threshold, enabled);
                });
    }

    public Mono<Void> deleteRule(String username, long id) {
        return repository.findRule(username, id)
                .switchIfEmpty(notFound("알림 규칙을 찾을 수 없습니다"))
                .flatMap(rule -> repository.deleteRule(username, id));
    }

    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Flux<AlertEvent> evaluate(String username) {
        return repository.findEnabledRules(username).concatMap(rule ->
                kiwoomApiService.getStockCurrentPrice(rule.code())
                        .map(response -> new BigDecimal(response.getCurrentPrice()))
                        .flatMap(value -> evaluateRule(username, rule, value)));
    }

    private Mono<AlertEvent> evaluateRule(String username, AlertRule rule, BigDecimal value) {
        boolean matched = rule.conditionType() == AlertConditionType.PRICE_ABOVE
                ? value.compareTo(rule.threshold()) >= 0
                : value.compareTo(rule.threshold()) <= 0;
        if (!matched) return repository.resetState(username, rule.id()).then(Mono.empty());
        return repository.transitionToTriggered(username, rule.id())
                .flatMap(changed -> changed ? repository.addEvent(username, rule, value) : Mono.empty());
    }

    public Flux<AlertEvent> findEvents(String username, boolean unreadOnly) {
        return repository.findEvents(username, unreadOnly);
    }

    public Mono<Void> markRead(String username, long id) {
        return repository.findEvent(username, id)
                .switchIfEmpty(notFound("알림 이벤트를 찾을 수 없습니다"))
                .flatMap(event -> repository.markRead(username, id));
    }

    private AlertRuleRequest validate(AlertRuleRequest request) {
        if (request == null || request.code() == null || !request.code().trim().matches("\\d{6}"))
            throw new IllegalArgumentException("종목 코드는 6자리 숫자여야 합니다");
        if (request.conditionType() == null) throw new IllegalArgumentException("알림 조건은 필수입니다");
        if (request.threshold() == null || request.threshold().signum() <= 0)
            throw new IllegalArgumentException("목표가는 0보다 커야 합니다");
        return new AlertRuleRequest(request.code().trim(), request.conditionType(), request.threshold());
    }

    private <T> Mono<T> notFound(String message) {
        return Mono.error(new ResourceNotFoundException(message));
    }
}
