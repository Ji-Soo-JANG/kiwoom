package com.example.kiwoom.integration;

import com.example.kiwoom.client.KiwoomHttpClient;
import com.example.kiwoom.config.KiwoomApiProperties;
import com.example.kiwoom.dto.DailyPriceResponse;
import com.example.kiwoom.dto.StockPriceResponse;
import com.example.kiwoom.mapper.KiwoomResponseMapper;
import com.example.kiwoom.service.KiwoomApiService;
import com.example.kiwoom.service.TechnicalIndicatorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class KiwoomLiveApiIT {
    @Test
    void readsCurrentAndDailyPricesFromSelectedKiwoomEnvironment() {
        String environment = environment();
        String prefix = "KIWOOM_" + environment + "_";
        String baseUrl = required(prefix + "BASE_URL");
        String appKey = required(prefix + "APP_KEY");
        String secretKey = required(prefix + "SECRET_KEY");
        String code = System.getenv().getOrDefault("KIWOOM_LIVE_STOCK_CODE", "005930");

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        KiwoomApiProperties properties = new KiwoomApiProperties(
                baseUrl, appKey, secretKey, Duration.ofSeconds(3), Duration.ofSeconds(10),
                5, 1, Duration.ofMillis(300), Duration.ZERO, Duration.ZERO);
        KiwoomApiService service = new KiwoomApiService(
                new KiwoomHttpClient(WebClient.create(), properties, registry),
                new KiwoomResponseMapper(new ObjectMapper()), properties, registry,
                new TechnicalIndicatorService());

        StockPriceResponse current = service.getStockCurrentPrice(code).block(Duration.ofSeconds(20));
        List<DailyPriceResponse> daily = service.getDailyPrices(code, null).block(Duration.ofSeconds(30));

        assertNotNull(current);
        assertEquals(code, current.getCode());
        assertTrue(Long.parseLong(current.getCurrentPrice()) > 0);
        assertNotNull(daily);
        assertFalse(daily.isEmpty());
        assertTrue(daily.stream().allMatch(item -> item.getDate().matches("\\d{8}")));
    }

    private String environment() {
        String value = System.getenv().getOrDefault("KIWOOM_LIVE_ENV", "PAPER")
                .trim().toUpperCase(Locale.ROOT);
        if (!value.equals("PAPER") && !value.equals("PROD")) {
            throw new IllegalStateException("KIWOOM_LIVE_ENV는 PAPER 또는 PROD여야 합니다");
        }
        return value;
    }

    private String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank() || value.startsWith("replace-with-")) {
            throw new IllegalStateException(name + " 환경변수를 실제 값으로 설정해야 합니다");
        }
        return value.trim();
    }
}
