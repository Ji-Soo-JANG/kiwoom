package com.example.kiwoom.service;

import com.example.kiwoom.repository.WatchlistRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class WatchlistService {
    private final WatchlistRepository repository;

    public WatchlistService(WatchlistRepository repository) {
        this.repository = repository;
    }

    public Mono<List<String>> findAll(String username) {
        return repository.findAll(username).collectList();
    }

    public Mono<String> add(String username, String code) {
        String normalized = validate(code);
        return repository.add(username, normalized).thenReturn(normalized);
    }

    public Mono<Void> remove(String username, String code) {
        return repository.remove(username, validate(code));
    }

    private String validate(String code) {
        if (code == null || !code.trim().matches("\\d{6}")) {
            throw new IllegalArgumentException("종목 코드는 6자리 숫자여야 합니다");
        }
        return code.trim();
    }
}
