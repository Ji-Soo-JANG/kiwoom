package com.example.kiwoom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.kiwoom.dto.PortfolioTrade;
import com.example.kiwoom.dto.PortfolioValuation;
import com.example.kiwoom.dto.TradeType;
import com.example.kiwoom.repository.PortfolioRepository;
import com.example.kiwoom.repository.PortfolioTradeRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class PortfolioTradeServiceTest {
    private PortfolioRepository portfolios;
    private PortfolioTradeRepository trades;
    private PortfolioTradeService service;

    @BeforeEach
    void setUp() {
        portfolios = mock(PortfolioRepository.class);
        trades = mock(PortfolioTradeRepository.class);
        service = new PortfolioTradeService(portfolios, trades);
    }

    @Test
    void validatesPageBounds() {
        assertThatThrownBy(() -> service.findAll("admin", -1, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.findAll("admin", 0, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exportsTradesAsCsv() {
        when(trades.findAll("admin", 0, 10_000)).thenReturn(Flux.just(trade("005930", "10")));

        assertThat(service.exportCsv("admin").block())
                .startsWith("code,type")
                .contains("005930,BUY");
    }

    @Test
    void rejectsEmptyAndMalformedCsv() {
        assertThatThrownBy(() -> service.importCsv("admin", " ").blockLast())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.importCsv("admin", "005930,BUY,broken").blockLast())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1번째 줄");
    }

    @Test
    void buildsDailyProfitTrendAndAddsCurrentUnrealizedProfit() {
        OffsetDateTime now = OffsetDateTime.now();
        when(trades.findAll("admin", 0, 10_000))
                .thenReturn(
                        Flux.just(
                                trade("005930", "10", now),
                                trade("000660", "20", now.plusMinutes(1))));
        PortfolioService portfolioService = mock(PortfolioService.class);
        when(portfolioService.valuate("admin"))
                .thenReturn(
                        Mono.just(
                                List.of(
                                        new PortfolioValuation(
                                                "005930",
                                                BigDecimal.ONE,
                                                BigDecimal.ONE,
                                                BigDecimal.ONE,
                                                BigDecimal.ONE,
                                                BigDecimal.ONE,
                                                new BigDecimal("5"),
                                                BigDecimal.ONE))));

        var points = service.profitTrend("admin", portfolioService).block();

        assertThat(points).hasSize(1);
        assertThat(points.getFirst().realizedProfitLoss()).isEqualByComparingTo("30");
        assertThat(points.getFirst().unrealizedProfitLoss()).isEqualByComparingTo("5");
    }

    private PortfolioTrade trade(String code, String realized) {
        return trade(code, realized, OffsetDateTime.now());
    }

    private PortfolioTrade trade(String code, String realized, OffsetDateTime tradedAt) {
        return new PortfolioTrade(
                1L,
                code,
                TradeType.BUY,
                BigDecimal.ONE,
                BigDecimal.TEN,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal(realized),
                tradedAt);
    }
}
