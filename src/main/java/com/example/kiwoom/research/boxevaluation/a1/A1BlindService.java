package com.example.kiwoom.research.boxevaluation.a1;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class A1BlindService {
    private final A1BlindRepository repository;

    public A1BlindService(A1BlindRepository repository) {
        this.repository = repository;
    }

    public Mono<A1BlindPayload> payload(long itemId) {
        return repository.findPayload(itemId);
    }
}
