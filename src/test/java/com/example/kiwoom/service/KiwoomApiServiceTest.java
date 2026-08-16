package com.example.kiwoom.service;

import com.example.kiwoom.config.KiwoomApiProperties;
import com.example.kiwoom.client.KiwoomHttpClient;
import com.example.kiwoom.dto.StockPriceResponse;
import com.example.kiwoom.mapper.KiwoomResponseMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("KiwoomApiService 단위 테스트")
class KiwoomApiServiceTest {

    private MockWebServer server;
    private KiwoomApiService service;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        service = createServiceWithCache(Duration.ZERO);
    }

    private KiwoomApiProperties properties(Duration cacheTtl) {
        return new KiwoomApiProperties(
                        server.url("/").toString(),
                        "test-key",
                        "test-secret",
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(2),
                        5,
                        2,
                        Duration.ofMillis(1), cacheTtl
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    @DisplayName("서비스 인스턴스를 생성한다")
    void createsService() {
        assertNotNull(service);
    }

    @Test
    @DisplayName("null 또는 빈 종목 코드를 거부한다")
    void rejectsBlankCode() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getStockCurrentPrice(null).block());
        assertThrows(IllegalArgumentException.class,
                () -> service.getStockCurrentPrice("   ").block());
    }

    @Test
    @DisplayName("6자리 숫자가 아닌 종목 코드를 거부한다")
    void rejectsInvalidCode() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getStockCurrentPrice("A05930").block());
        assertThrows(IllegalArgumentException.class,
                () -> service.getStockCurrentPrice("5930").block());
    }

    @Test
    @DisplayName("유효하지 않은 기준일자를 거부한다")
    void rejectsInvalidBaseDate() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getDailyPrices("005930", "20260230").block());
    }

    @Test
    @DisplayName("다중 조회 종목 수를 20개로 제한한다")
    void limitsMultipleCodes() {
        List<String> codes = java.util.stream.IntStream.range(0, 21)
                .mapToObj(value -> String.format("%06d", value))
                .toList();

        assertThrows(IllegalArgumentException.class,
                () -> service.getMultipleStockPrices(codes).block());
    }

    @Test
    @DisplayName("다중 조회 중 한 종목이 실패하면 전체 요청을 실패 처리한다")
    void failsEntireMultipleRequestWhenOneStockFails() {
        enqueueJson(200, """
                {"return_code":0,"token":"access-token","expires_dt":"20991231235959"}
                """);
        enqueueJson(200, """
                {"return_code":0,"stk_cd":"005930","cur_prc":"75000"}
                """);
        enqueueJson(400, "invalid stock");

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.getMultipleStockPrices(List.of("005930", "000660")).block());

        assertTrue(error.getMessage().contains("주가 조회 API 호출 실패"));
    }

    @Test
    @DisplayName("토큰을 발급받아 현재가 응답을 변환한다")
    void getsCurrentPrice() throws InterruptedException {
        enqueueJson(200, """
                {"return_code":0,"token":"access-token","expires_dt":"20991231235959"}
                """);
        enqueueJson(200, """
                {"return_code":0,"stk_cd":"005930","cur_prc":"-75,000","pred_pre":"+500","flu_rt":"+0.67"}
                """);

        StockPriceResponse response = service.getStockCurrentPrice("005930").block();

        assertNotNull(response);
        assertEquals("005930", response.getCode());
        assertEquals("75000", response.getCurrentPrice());
        assertEquals("+500", response.getChangeAmount());
        assertEquals("+0.67", response.getChangeRate());
        assertEquals("/oauth2/token", server.takeRequest().getPath());
        assertEquals("/api/dostk/stkinfo", server.takeRequest().getPath());
    }

    @Test
    @DisplayName("TTL 안의 같은 종목 현재가는 키움 API를 한 번만 호출한다")
    void cachesCurrentPriceWithinTtl() {
        service = createServiceWithCache(Duration.ofMinutes(1));
        enqueueJson(200, """
                {"return_code":0,"token":"access-token","expires_dt":"20991231235959"}
                """);
        enqueueJson(200, """
                {"return_code":0,"stk_cd":"005930","cur_prc":"75000"}
                """);

        assertEquals("75000", service.getStockCurrentPrice("005930").block().getCurrentPrice());
        assertEquals("75000", service.getStockCurrentPrice("005930").block().getCurrentPrice());
        assertEquals(2, server.getRequestCount());
    }

    @Test
    @DisplayName("401 응답이면 토큰을 갱신하고 한 번 재시도한다")
    void refreshesTokenAfterUnauthorized() throws InterruptedException {
        enqueueJson(200, """
                {"return_code":0,"token":"expired-token","expires_dt":"20991231235959"}
                """);
        enqueueJson(401, "unauthorized");
        enqueueJson(200, """
                {"return_code":0,"token":"new-token","expires_dt":"20991231235959"}
                """);
        enqueueJson(200, """
                {"return_code":0,"stk_cd":"005930","cur_prc":"75000"}
                """);

        StockPriceResponse response = service.getStockCurrentPrice("005930").block();

        assertNotNull(response);
        assertEquals("75000", response.getCurrentPrice());
        assertEquals("/oauth2/token", server.takeRequest().getPath());
        assertEquals("/api/dostk/stkinfo", server.takeRequest().getPath());
        assertEquals("/oauth2/token", server.takeRequest().getPath());
        assertEquals("/api/dostk/stkinfo", server.takeRequest().getPath());
    }

    @Test
    @DisplayName("만료된 토큰은 다음 요청 전에 재발급한다")
    void refreshesExpiredToken() throws InterruptedException {
        enqueueJson(200, """
                {"return_code":0,"token":"old-token","expires_dt":"20200101000000"}
                """);
        enqueueJson(200, """
                {"return_code":0,"stk_cd":"005930","cur_prc":"70000"}
                """);
        enqueueJson(200, """
                {"return_code":0,"token":"new-token","expires_dt":"20991231235959"}
                """);
        enqueueJson(200, """
                {"return_code":0,"stk_cd":"005930","cur_prc":"71000"}
                """);

        assertEquals("70000", service.getStockCurrentPrice("005930").block().getCurrentPrice());
        assertEquals("71000", service.getStockCurrentPrice("005930").block().getCurrentPrice());

        assertEquals("/oauth2/token", server.takeRequest().getPath());
        assertEquals("/api/dostk/stkinfo", server.takeRequest().getPath());
        assertEquals("/oauth2/token", server.takeRequest().getPath());
        assertEquals("/api/dostk/stkinfo", server.takeRequest().getPath());
    }

    @Test
    @DisplayName("키움 HTTP 오류를 예외로 전달한다")
    void propagatesHttpError() {
        enqueueJson(500, "server error");
        enqueueJson(500, "server error");
        enqueueJson(500, "server error");

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.getStockCurrentPrice("005930").block());

        assertTrue(error.getMessage().contains("일시적인 키움 API 오류"));
    }

    @Test
    @DisplayName("429 응답이면 지수 백오프로 재시도한다")
    void retriesRateLimitResponse() throws InterruptedException {
        enqueueJson(200, """
                {"return_code":0,"token":"access-token","expires_dt":"20991231235959"}
                """);
        enqueueJson(429, "rate limited");
        enqueueJson(200, """
                {"return_code":0,"stk_cd":"005930","cur_prc":"72000"}
                """);

        StockPriceResponse response = service.getStockCurrentPrice("005930").block();

        assertNotNull(response);
        assertEquals("72000", response.getCurrentPrice());
        assertEquals("/oauth2/token", server.takeRequest().getPath());
        assertEquals("/api/dostk/stkinfo", server.takeRequest().getPath());
        assertEquals("/api/dostk/stkinfo", server.takeRequest().getPath());
    }

    @Test
    @DisplayName("잘못된 토큰 JSON을 예외로 전달한다")
    void rejectsMalformedJson() {
        enqueueJson(200, "not-json");

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.getStockCurrentPrice("005930").block());

        assertTrue(error.getMessage().contains("토큰 응답 JSON 파싱 실패"));
    }

    private void enqueueJson(int status, String body) {
        server.enqueue(new MockResponse()
                .setResponseCode(status)
                .setHeader("Content-Type", "application/json")
                .setBody(body));
    }

    private KiwoomApiService createServiceWithCache(Duration cacheTtl) {
        KiwoomApiProperties properties = properties(cacheTtl);
        ObjectMapper objectMapper = new ObjectMapper();
        return new KiwoomApiService(
                new KiwoomHttpClient(WebClient.create(), properties),
                new KiwoomResponseMapper(objectMapper),
                properties
        );
    }
}
