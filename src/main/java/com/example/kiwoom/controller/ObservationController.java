package com.example.kiwoom.controller;

import com.example.kiwoom.dto.ObservationReport;
import com.example.kiwoom.dto.ObservationRequest;
import com.example.kiwoom.dto.ObservationSampleRequest;
import com.example.kiwoom.service.ObservationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/observations")
public class ObservationController {
    private final ObservationService service;

    public ObservationController(ObservationService service) {
        this.service = service;
    }

    @PostMapping
    public Mono<ObservationReport> create(@Valid @RequestBody ObservationRequest request) {
        return service.create(request);
    }

    @PostMapping("/{id}/samples")
    public Mono<ObservationReport> addSample(
            @PathVariable long id, @Valid @RequestBody ObservationSampleRequest request) {
        return service.addSample(id, request);
    }

    @GetMapping("/{id}")
    public Mono<ObservationReport> report(@PathVariable long id) {
        return service.report(id);
    }
}
