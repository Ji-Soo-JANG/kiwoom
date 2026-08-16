package com.example.kiwoom.controller;

import com.example.kiwoom.dto.WatchlistRequest;
import com.example.kiwoom.service.WatchlistService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import java.util.List;

@RestController
@RequestMapping("/api/watchlist")
@Tag(name = "Watchlist", description = "관심종목 관리")
public class WatchlistController {
    private final WatchlistService service;

    public WatchlistController(WatchlistService service) { this.service = service; }

    @GetMapping
    public Mono<List<String>> findAll() { return service.findAll(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<String> add(@RequestBody WatchlistRequest request) { return service.add(request.code()); }

    @DeleteMapping("/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> remove(@PathVariable String code) { return service.remove(code); }
}
