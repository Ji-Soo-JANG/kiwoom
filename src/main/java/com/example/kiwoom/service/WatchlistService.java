package com.example.kiwoom.service;

import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

@Service
public class WatchlistService {
    private final Set<String> codes = new ConcurrentSkipListSet<>();

    public List<String> findAll() { return List.copyOf(codes); }

    public String add(String code) {
        String normalized = validate(code);
        codes.add(normalized);
        return normalized;
    }

    public void remove(String code) { codes.remove(validate(code)); }

    private String validate(String code) {
        if (code == null || !code.trim().matches("\\d{6}")) {
            throw new IllegalArgumentException("종목 코드는 6자리 숫자여야 합니다");
        }
        return code.trim();
    }
}
