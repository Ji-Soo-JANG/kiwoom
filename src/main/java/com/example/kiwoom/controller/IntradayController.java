package com.example.kiwoom.controller;

import com.example.kiwoom.dto.IntradayBar;
import com.example.kiwoom.dto.IntradayPriceEvent;
import com.example.kiwoom.dto.IntradayReplay;
import com.example.kiwoom.service.IntradayEventService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/intraday")
public class IntradayController {
    private final IntradayEventService service;

    public IntradayController(IntradayEventService service) {
        this.service = service;
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<IntradayPriceEvent> record(@Valid @RequestBody IntradayPriceEvent event) {
        return service.record(event);
    }

    @GetMapping("/{code}/replay")
    public Mono<IntradayReplay> replay(
            @PathVariable String code,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return service.replay(code, from, to);
    }

    @GetMapping("/{code}/bars")
    public Mono<List<IntradayBar>> bars(
            @PathVariable String code,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return service.bars(code, from, to);
    }
}
