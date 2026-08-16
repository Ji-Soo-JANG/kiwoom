package com.example.kiwoom.service;

import com.example.kiwoom.dto.*;
import com.example.kiwoom.error.ResourceNotFoundException;
import com.example.kiwoom.repository.AlertRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class AlertService {
    private final AlertRepository repository;
    private final KiwoomApiService kiwoomApiService;

    public AlertService(AlertRepository repository, KiwoomApiService kiwoomApiService) {
        this.repository = repository;
        this.kiwoomApiService = kiwoomApiService;
    }

    public Flux<AlertRule> findRules(String username) {
        return repository.findRules(username);
    }

    public Mono<AlertRule> addRule(String username, AlertRuleRequest request) {
        AlertRuleRequest validated = validate(request);
        return repository.addRule(username, validated);
    }

    public Mono<AlertRule> updateRule(String username, long id, AlertRuleUpdateRequest request) {
        return repository
                .findRule(username, id)
                .switchIfEmpty(notFound("알림 규칙을 찾을 수 없습니다"))
                .flatMap(
                        current -> {
                            BigDecimal threshold =
                                    request == null || request.threshold() == null
                                            ? current.threshold()
                                            : request.threshold();
                            boolean enabled =
                                    request == null || request.enabled() == null
                                            ? current.enabled()
                                            : request.enabled();
                            validateThreshold(current.conditionType(), threshold);
                            return repository.updateRule(username, id, threshold, enabled);
                        });
    }

    public Mono<Void> deleteRule(String username, long id) {
        return repository
                .findRule(username, id)
                .switchIfEmpty(notFound("알림 규칙을 찾을 수 없습니다"))
                .flatMap(rule -> repository.deleteRule(username, id));
    }

    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Flux<AlertEvent> evaluate(String username) {
        return repository
                .findEnabledRules(username)
                .concatMap(
                        rule ->
                                evaluateValue(rule)
                                        .flatMap(value -> evaluateRule(username, rule, value)));
    }

    private Mono<BigDecimal> evaluateValue(AlertRule rule) {
        if (rule.conditionType() == AlertConditionType.PRICE_ABOVE
                || rule.conditionType() == AlertConditionType.PRICE_BELOW) {
            return kiwoomApiService
                    .getStockCurrentPrice(rule.code())
                    .map(response -> new BigDecimal(response.getCurrentPrice()));
        }
        return kiwoomApiService
                .getDailyPrices(rule.code(), null)
                .flatMap(prices -> indicatorValue(rule.conditionType(), prices));
    }

    private Mono<BigDecimal> indicatorValue(
            AlertConditionType type, List<DailyPriceResponse> prices) {
        if (prices.isEmpty()) return Mono.empty();
        DailyPriceResponse latest = prices.get(prices.size() - 1);
        Double value =
                switch (type) {
                    case CHANGE_RATE_ABOVE, CHANGE_RATE_BELOW -> changeRate(prices);
                    case RSI_ABOVE, RSI_BELOW -> latest.getRsi();
                    case MACD_CROSS_UP, MACD_CROSS_DOWN ->
                            latest.getMacd() == null || latest.getSignal() == null
                                    ? null
                                    : latest.getMacd() - latest.getSignal();
                    default -> null;
                };
        return value == null ? Mono.empty() : Mono.just(BigDecimal.valueOf(value));
    }

    private Mono<AlertEvent> evaluateRule(String username, AlertRule rule, BigDecimal value) {
        boolean matched =
                switch (rule.conditionType()) {
                    case PRICE_ABOVE, RSI_ABOVE, CHANGE_RATE_ABOVE ->
                            value.compareTo(rule.threshold()) >= 0;
                    case PRICE_BELOW, RSI_BELOW -> value.compareTo(rule.threshold()) <= 0;
                    case CHANGE_RATE_BELOW -> value.compareTo(rule.threshold().negate()) <= 0;
                    case MACD_CROSS_UP -> value.signum() > 0;
                    case MACD_CROSS_DOWN -> value.signum() < 0;
                };
        if (!matched) return repository.resetState(username, rule.id()).then(Mono.empty());
        return repository
                .transitionToTriggered(username, rule.id())
                .flatMap(
                        changed ->
                                changed
                                        ? repository.addEvent(username, rule, value)
                                        : Mono.empty());
    }

    private Double changeRate(List<DailyPriceResponse> prices) {
        if (prices.size() < 2) return null;
        long previousClose = prices.get(prices.size() - 2).getClosePrice();
        if (previousClose == 0) return null;
        long latestClose = prices.get(prices.size() - 1).getClosePrice();
        return (latestClose - previousClose) * 100d / previousClose;
    }

    public Mono<PageResponse<AlertEvent>> findEvents(
            String username, boolean unreadOnly, int page, int size) {
        if (page < 0) throw new IllegalArgumentException("페이지 번호는 0 이상이어야 합니다");
        if (size < 1 || size > 100) throw new IllegalArgumentException("페이지 크기는 1부터 100 사이여야 합니다");
        return Mono.zip(
                        repository.findEvents(username, unreadOnly, page, size).collectList(),
                        repository.countEvents(username, unreadOnly))
                .map(tuple -> new PageResponse<>(tuple.getT1(), page, size, tuple.getT2()));
    }

    public Mono<Void> markRead(String username, long id) {
        return repository
                .findEvent(username, id)
                .switchIfEmpty(notFound("알림 이벤트를 찾을 수 없습니다"))
                .flatMap(event -> repository.markRead(username, id));
    }

    private AlertRuleRequest validate(AlertRuleRequest request) {
        if (request == null || request.code() == null || !request.code().trim().matches("\\d{6}"))
            throw new IllegalArgumentException("종목 코드는 6자리 숫자여야 합니다");
        if (request.conditionType() == null) throw new IllegalArgumentException("알림 조건은 필수입니다");
        validateThreshold(request.conditionType(), request.threshold());
        return new AlertRuleRequest(
                request.code().trim(), request.conditionType(), request.threshold());
    }

    private void validateThreshold(AlertConditionType type, BigDecimal threshold) {
        if (!type.requiresThreshold()) {
            if (threshold != null)
                throw new IllegalArgumentException("MACD 교차 조건에는 기준값을 입력하지 않습니다");
            return;
        }
        if (threshold == null) throw new IllegalArgumentException("기준값은 필수입니다");
        if ((type == AlertConditionType.PRICE_ABOVE || type == AlertConditionType.PRICE_BELOW)
                && threshold.signum() <= 0) throw new IllegalArgumentException("목표가는 0보다 커야 합니다");
        if ((type == AlertConditionType.RSI_ABOVE || type == AlertConditionType.RSI_BELOW)
                && (threshold.compareTo(BigDecimal.ZERO) < 0
                        || threshold.compareTo(BigDecimal.valueOf(100)) > 0))
            throw new IllegalArgumentException("RSI 기준값은 0부터 100 사이여야 합니다");
        if ((type == AlertConditionType.CHANGE_RATE_ABOVE
                        || type == AlertConditionType.CHANGE_RATE_BELOW)
                && (threshold.signum() <= 0 || threshold.compareTo(BigDecimal.valueOf(100)) > 0))
            throw new IllegalArgumentException("등락률 기준값은 0 초과 100 이하여야 합니다");
    }

    private <T> Mono<T> notFound(String message) {
        return Mono.error(new ResourceNotFoundException(message));
    }
}
