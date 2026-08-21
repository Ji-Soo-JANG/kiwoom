package com.example.kiwoom.service;

import com.example.kiwoom.dto.IntradayBar;
import com.example.kiwoom.dto.IntradayPriceEvent;
import com.example.kiwoom.dto.IntradayReplay;
import com.example.kiwoom.repository.IntradayEventRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class IntradayEventService {
    private final IntradayEventRepository repository;
    private final PaperTradeCycleService tradeCycles;

    public IntradayEventService(
            IntradayEventRepository repository, PaperTradeCycleService tradeCycles) {
        this.repository = repository;
        this.tradeCycles = tradeCycles;
    }

    public Mono<IntradayPriceEvent> record(IntradayPriceEvent event) {
        if (event.eventTime().isAfter(Instant.now().plusSeconds(30))) {
            return Mono.error(new IllegalArgumentException("미래 시각의 시세 이벤트는 저장할 수 없습니다."));
        }
        return repository
                .save(event)
                .flatMap(
                        saved ->
                                tradeCycles
                                        .evaluate(
                                                saved.code(),
                                                BigDecimal.valueOf(saved.price()),
                                                saved.eventTime())
                                        .thenReturn(saved));
    }

    public Mono<IntradayReplay> replay(String code, Instant from, Instant to) {
        validateRange(from, to);
        return repository
                .replay(code, from, to)
                .collectList()
                .map(
                        events ->
                                new IntradayReplay(
                                        code,
                                        events.size(),
                                        events.isEmpty() ? null : events.get(0).eventTime(),
                                        events.isEmpty()
                                                ? null
                                                : events.get(events.size() - 1).eventTime(),
                                        checksum(events),
                                        List.copyOf(events)));
    }

    public Mono<List<IntradayBar>> bars(String code, Instant from, Instant to) {
        validateRange(from, to);
        return repository.replay(code, from, to).collectList().map(this::aggregateMinutes);
    }

    private List<IntradayBar> aggregateMinutes(List<IntradayPriceEvent> events) {
        Map<Instant, MutableBar> bars = new LinkedHashMap<>();
        for (IntradayPriceEvent event : events) {
            Instant minute = event.eventTime().truncatedTo(ChronoUnit.MINUTES);
            bars.computeIfAbsent(minute, ignored -> new MutableBar(event.price()))
                    .accept(event.price(), event.volume());
        }
        List<IntradayBar> result = new ArrayList<>();
        bars.forEach(
                (minute, bar) ->
                        result.add(
                                new IntradayBar(
                                        minute,
                                        bar.open,
                                        bar.high,
                                        bar.low,
                                        bar.close,
                                        bar.volume,
                                        bar.count)));
        return List.copyOf(result);
    }

    private String checksum(List<IntradayPriceEvent> events) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (IntradayPriceEvent event : events) {
                digest.update(
                        (event.sourceEventId()
                                        + '|'
                                        + event.code()
                                        + '|'
                                        + event.eventTime()
                                        + '|'
                                        + event.price()
                                        + '|'
                                        + event.volume()
                                        + '\n')
                                .getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", error);
        }
    }

    private void validateRange(Instant from, Instant to) {
        if (from.isAfter(to)) throw new IllegalArgumentException("재생 시작 시각은 종료 시각보다 늦을 수 없습니다.");
        if (from.isBefore(to.minus(7, ChronoUnit.DAYS))) {
            throw new IllegalArgumentException("한 번에 재생할 수 있는 범위는 최대 7일입니다.");
        }
    }

    private static final class MutableBar {
        private final long open;
        private long high;
        private long low;
        private long close;
        private long volume;
        private int count;

        private MutableBar(long price) {
            open = price;
            high = price;
            low = price;
            close = price;
        }

        private void accept(long price, long eventVolume) {
            high = Math.max(high, price);
            low = Math.min(low, price);
            close = price;
            volume += eventVolume;
            count++;
        }
    }
}
