package com.example.kiwoom.controller;

import com.example.kiwoom.dto.*;
import com.example.kiwoom.service.AlertService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/alerts")
@Tag(name = "Alerts", description = "목표가 앱 내부 알림")
public class AlertController {
    private final AlertService service;

    public AlertController(AlertService service) {
        this.service = service;
    }

    @GetMapping("/rules")
    public Flux<AlertRule> findRules(Principal principal) {
        return service.findRules(principal.getName());
    }

    @PostMapping("/rules")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<AlertRule> addRule(
            Principal principal, @Valid @RequestBody AlertRuleRequest request) {
        return service.addRule(principal.getName(), request);
    }

    @PatchMapping("/rules/{id}")
    public Mono<AlertRule> updateRule(
            Principal principal,
            @PathVariable long id,
            @RequestBody AlertRuleUpdateRequest request) {
        return service.updateRule(principal.getName(), id, request);
    }

    @DeleteMapping("/rules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteRule(Principal principal, @PathVariable long id) {
        return service.deleteRule(principal.getName(), id);
    }

    @PostMapping("/evaluate")
    public Flux<AlertEvent> evaluate(Principal principal) {
        return service.evaluate(principal.getName());
    }

    @GetMapping("/events")
    public Mono<PageResponse<AlertEvent>> findEvents(
            Principal principal,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.findEvents(principal.getName(), unreadOnly, page, size);
    }

    @PostMapping("/events/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> markRead(Principal principal, @PathVariable long id) {
        return service.markRead(principal.getName(), id);
    }
}
