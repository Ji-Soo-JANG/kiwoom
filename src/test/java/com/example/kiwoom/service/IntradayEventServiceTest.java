package com.example.kiwoom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.kiwoom.dto.IntradayPriceEvent;
import com.example.kiwoom.repository.IntradayEventRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;

class IntradayEventServiceTest {
    private final IntradayEventRepository repository = Mockito.mock(IntradayEventRepository.class);
    private final PaperTradeCycleService tradeCycles = Mockito.mock(PaperTradeCycleService.class);
    private final IntradayEventService service = new IntradayEventService(repository, tradeCycles);

    @Test
    void replayIsDeterministicAndMinuteBarsUseOhlcv() {
        Instant from = Instant.parse("2026-08-20T00:00:00Z");
        Instant to = from.plusSeconds(120);
        var events =
                Flux.just(
                        event("e1", from.plusSeconds(1), 100, 10),
                        event("e2", from.plusSeconds(20), 120, 20),
                        event("e3", from.plusSeconds(50), 90, 30),
                        event("e4", from.plusSeconds(70), 110, 40));
        when(repository.replay("005930", from, to)).thenAnswer(ignored -> events);

        var replay = service.replay("005930", from, to).block();
        assertThat(replay).isNotNull();
        assertThat(replay.eventCount()).isEqualTo(4);
        assertThat(replay.checksum()).hasSize(64);

        var bars = service.bars("005930", from, to).block();
        assertThat(bars).hasSize(2);
        assertThat(bars.get(0).openPrice()).isEqualTo(100);
        assertThat(bars.get(0).highPrice()).isEqualTo(120);
        assertThat(bars.get(0).lowPrice()).isEqualTo(90);
        assertThat(bars.get(0).closePrice()).isEqualTo(90);
        assertThat(bars.get(0).volume()).isEqualTo(60);
    }

    private IntradayPriceEvent event(String id, Instant time, long price, long volume) {
        return new IntradayPriceEvent(null, id, "005930", time, price, volume, null);
    }
}
