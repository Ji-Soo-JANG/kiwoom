package com.example.kiwoom.controller;

import com.example.kiwoom.dto.WatchlistItem;
import com.example.kiwoom.dto.WatchlistRequest;
import com.example.kiwoom.service.WatchlistService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/watchlist")
@Tag(name = "Watchlist", description = "관심종목 관리")
public class WatchlistController {
    private final WatchlistService service;

    public WatchlistController(WatchlistService service) {
        this.service = service;
    }

    @GetMapping
    public Mono<List<WatchlistItem>> findAll(Principal principal) {
        return service.findAll(principal.getName());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<WatchlistItem> add(
            Principal principal, @Valid @RequestBody WatchlistRequest request) {
        return service.save(principal.getName(), request);
    }

    @PutMapping("/{code}")
    public Mono<WatchlistItem> update(
            Principal principal,
            @PathVariable String code,
            @Valid @RequestBody WatchlistRequest request) {
        return service.save(
                principal.getName(),
                new WatchlistRequest(code, request.groupName(), request.note()));
    }

    @DeleteMapping("/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> remove(Principal principal, @PathVariable String code) {
        return service.remove(principal.getName(), code);
    }
}
