package com.example.kiwoom.research.boxevaluation.controller;

import com.example.kiwoom.research.boxevaluation.a1.A1BlindPayload;
import com.example.kiwoom.research.boxevaluation.a1.A1BlindService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/** A1-only blind boundary; the existing TASK-002 endpoints remain unchanged. */
@RestController
@RequestMapping("/api/research/box-evaluations/a1")
public class A1BlindController {
    private final A1BlindService service;

    public A1BlindController(A1BlindService service) {
        this.service = service;
    }

    @GetMapping("/items/{itemId}/blind")
    public Mono<A1BlindPayload> blind(@PathVariable long itemId) {
        return service.payload(itemId);
    }
}
