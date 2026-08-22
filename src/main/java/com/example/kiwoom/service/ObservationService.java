package com.example.kiwoom.service;

import com.example.kiwoom.dto.ObservationReport;
import com.example.kiwoom.dto.ObservationRequest;
import com.example.kiwoom.dto.ObservationSampleRequest;
import com.example.kiwoom.repository.ObservationRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ObservationService {
    private final ObservationRepository repository;

    public ObservationService(ObservationRepository repository) {
        this.repository = repository;
    }

    public Mono<ObservationReport> create(ObservationRequest request) {
        return repository.create(request).flatMap(repository::report);
    }

    public Mono<ObservationReport> addSample(long id, ObservationSampleRequest request) {
        return repository.addSample(id, request).then(repository.report(id));
    }

    public Mono<ObservationReport> report(long id) {
        return repository.report(id);
    }

    public Mono<ObservationReport> latestOrCreate(String strategyVersion) {
        return repository
                .latest(strategyVersion)
                .switchIfEmpty(create(new ObservationRequest("자동 장중 관찰", strategyVersion)));
    }
}
