package com.example.kiwoom.service;

import com.example.kiwoom.dto.WatchlistItem;
import com.example.kiwoom.dto.WatchlistRequest;
import com.example.kiwoom.repository.MarketDataRepository;
import com.example.kiwoom.repository.WatchlistRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class WatchlistService {
    private final WatchlistRepository repository;
    private final MarketDataRepository marketDataRepository;

    public WatchlistService(
            WatchlistRepository repository, MarketDataRepository marketDataRepository) {
        this.repository = repository;
        this.marketDataRepository = marketDataRepository;
    }

    public Mono<List<WatchlistItem>> findAll(String username) {
        return repository
                .findAll(username)
                .collectList()
                .flatMap(
                        items -> {
                            List<String> codes = items.stream().map(WatchlistItem::code).toList();
                            return marketDataRepository
                                    .findStocksByCodes(codes)
                                    .collectMap(r -> r.code(), r -> r.name())
                                    .map(
                                            nameMap ->
                                                    items.stream()
                                                            .map(
                                                                    item ->
                                                                            new WatchlistItem(
                                                                                    item.code(),
                                                                                    nameMap
                                                                                            .getOrDefault(
                                                                                                    item
                                                                                                            .code(),
                                                                                                    item
                                                                                                            .code()),
                                                                                    item
                                                                                            .groupName(),
                                                                                    item.note()))
                                                            .toList())
                                    .onErrorResume(e -> Mono.just(items));
                        });
    }

    public Mono<WatchlistItem> save(String username, WatchlistRequest request) {
        String code = validate(request.code());
        String groupName = normalizeText(request.groupName(), "기본", 50, "그룹명");
        String note = normalizeText(request.note(), "", 500, "메모");
        WatchlistItem item = new WatchlistItem(code, null, groupName, note);
        return repository.save(username, item).thenReturn(item);
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

    private String normalizeText(String value, String defaultValue, int maxLength, String label) {
        String normalized = value == null || value.isBlank() ? defaultValue : value.trim();
        if (normalized.length() > maxLength) throw new IllegalArgumentException(label + "이 너무 깁니다");
        return normalized;
    }
}
