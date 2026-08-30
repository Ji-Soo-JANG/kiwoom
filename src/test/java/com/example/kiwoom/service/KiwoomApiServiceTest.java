package com.example.kiwoom.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.kiwoom.broker.kiwoom.client.KiwoomHttpClient;
import com.example.kiwoom.broker.kiwoom.mapper.KiwoomResponseMapper;
import com.example.kiwoom.config.KiwoomApiProperties;
import com.example.kiwoom.dto.StockPriceResponse;
import com.example.kiwoom.dto.StockSearchResult;
import com.example.kiwoom.error.KiwoomApiException;
import com.example.kiwoom.error.KiwoomErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;

@DisplayName("KiwoomApiService 단위 테스트")
class KiwoomApiServiceTest {

    private MockWebServer server;
    private KiwoomApiService service;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        meterRegistry = new SimpleMeterRegistry();
        service = createServiceWithCache(Duration.ZERO);
    }

    private KiwoomApiProperties properties(Duration cacheTtl) {
        return properties(cacheTtl, Duration.ZERO);
    }

    private KiwoomApiProperties properties(Duration cacheTtl, Duration dailyCacheTtl) {
        return new KiwoomApiProperties(
                server.url("/").toString(),
                "test-key",
                "test-secret",
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                5,
                2,
                Duration.ofMillis(1),
                cacheTtl,
                dailyCacheTtl);
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
        assertThrows(
                IllegalArgumentException.class, () -> service.getStockCurrentPrice(null).block());
        assertThrows(
                IllegalArgumentException.class, () -> service.getStockCurrentPrice("   ").block());
    }

    @Test
    @DisplayName("6자리 숫자가 아닌 종목 코드를 거부한다")
    void rejectsInvalidCode() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.getStockCurrentPrice("A05930").block());
        assertThrows(
                IllegalArgumentException.class, () -> service.getStockCurrentPrice("5930").block());
    }

    @Test
    @DisplayName("유효하지 않은 기준일자를 거부한다")
    void rejectsInvalidBaseDate() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.getDailyPrices("005930", "20260230").block());
    }

    @Test
    @DisplayName("다중 조회 종목 수를 20개로 제한한다")
    void limitsMultipleCodes() {
        List<String> codes =
                java.util.stream.IntStream.range(0, 21)
                        .mapToObj(value -> String.format("%06d", value))
                        .toList();

        assertThrows(
                IllegalArgumentException.class,
                () -> service.getMultipleStockPrices(codes).block());
    }

    @Test
    @DisplayName("다중 조회 중 한 종목이 실패하면 전체 요청을 실패 처리한다")
    void failsEntireMultipleRequestWhenOneStockFails() {
        enqueueJson(
                200,
                """
                {"return_code":0,"token":"access-token","expires_dt":"20991231235959"}
                """);
        enqueueJson(
                200,
                """
                {"return_code":0,"stk_cd":"005930","cur_prc":"75000"}
                """);
        enqueueJson(400, "invalid stock");

        RuntimeException error =
                assertThrows(
                        RuntimeException.class,
                        () -> service.getMultipleStockPrices(List.of("005930", "000660")).block());

        assertTrue(error.getMessage().contains("주가 조회 API 호출 실패"));
    }

    @Test
    @DisplayName("토큰을 발급받아 현재가 응답을 변환한다")
    void getsCurrentPrice() throws InterruptedException {
        enqueueJson(
                200,
                """
                {"return_code":0,"token":"access-token","expires_dt":"20991231235959"}
                """);
        enqueueJson(
                200,
                """
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
    @DisplayName("API secret과 접근 토큰을 로그에 남기지 않는다")
    void doesNotLogCredentialsOrAccessToken() {
        Logger logger = (Logger) LoggerFactory.getLogger(KiwoomApiService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            enqueueJson(
                    200,
                    """
                    {"return_code":0,"token":"sensitive-access-token","expires_dt":"20991231235959"}
                    """);
            enqueueJson(
                    200,
                    """
                    {"return_code":0,"stk_cd":"005930","cur_prc":"75000"}
                    """);

            service.getStockCurrentPrice("005930").block();

            String logs =
                    appender.list.stream()
                            .map(ILoggingEvent::getFormattedMessage)
                            .reduce("", (left, right) -> left + right);
            assertTrue(!logs.contains("test-secret"));
            assertTrue(!logs.contains("sensitive-access-token"));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    @DisplayName("TTL 안의 같은 종목 현재가는 키움 API를 한 번만 호출한다")
    void cachesCurrentPriceWithinTtl() {
        service = createServiceWithCache(Duration.ofMinutes(1));
        enqueueJson(
                200,
                """
                {"return_code":0,"token":"access-token","expires_dt":"20991231235959"}
                """);
        enqueueJson(
                200,
                """
                {"return_code":0,"stk_cd":"005930","cur_prc":"75000"}
                """);

        assertEquals("75000", service.getStockCurrentPrice("005930").block().getCurrentPrice());
        assertEquals("75000", service.getStockCurrentPrice("005930").block().getCurrentPrice());
        assertEquals(2, server.getRequestCount());
        assertEquals(
                1,
                meterRegistry
                        .get("kiwoom.cache.accesses")
                        .tags("cache", "current", "result", "hit")
                        .counter()
                        .count());
    }

    @Test
    @DisplayName("TTL 안의 같은 종목·기준일 일봉은 키움 API를 한 번만 호출한다")
    void cachesDailyPricesByCodeAndDateWithinTtl() {
        KiwoomApiProperties properties = properties(Duration.ZERO, Duration.ofMinutes(1));
        service =
                new KiwoomApiService(
                        new KiwoomHttpClient(WebClient.create(), properties, meterRegistry),
                        new KiwoomResponseMapper(new ObjectMapper()),
                        properties,
                        meterRegistry,
                        new TechnicalIndicatorService());
        enqueueJson(
                200,
                """
                {"return_code":0,"token":"access-token","expires_dt":"20991231235959"}
                """);
        enqueueJson(
                200,
                """
                {"return_code":0,"stk_dt_pole_chart_qry":[
                  {"dt":"20260815","open_pric":"70000","high_pric":"71000","low_pric":"69000","cur_prc":"70500","trde_qty":"1000"}
                ]}
                """);

        assertEquals(1, service.getDailyPrices("005930", "20260816").block().size());
        assertEquals(1, service.getDailyPrices("005930", "20260816").block().size());
        assertEquals(2, server.getRequestCount());
        assertEquals(1, service.getDailyPriceCacheStats().hits());
        assertEquals(1, service.getDailyPriceCacheStats().apiCalls());
    }

    @Test
    @DisplayName("주기 파라미터에 맞는 차트 api-id로 요청한다")
    void requestsChartByPeriod() throws InterruptedException {
        enqueueJson(
                200,
                """
                {"return_code":0,"token":"access-token","expires_dt":"20991231235959"}
                """);
        String chartResponse =
                """
                {"return_code":0,"stk_dt_pole_chart_qry":[
                  {"dt":"20260815","open_pric":"70000","high_pric":"71000","low_pric":"69000","cur_prc":"70500","trde_qty":"1000"}
                ]}
                """;
        enqueueJson(200, chartResponse);
        enqueueJson(200, chartResponse);
        enqueueJson(200, chartResponse);
        enqueueJson(200, chartResponse);

        assertEquals(1, service.getPeriodPrices("005930", "20260816", 120, "week").block().size());
        assertEquals(1, service.getPeriodPrices("005930", "20260816", 120, "month").block().size());
        assertEquals(1, service.getPeriodPrices("005930", "20260816", 120, "year").block().size());
        assertEquals(
                1, service.getPeriodPrices("005930", "20260816", 120, "unknown").block().size());

        assertEquals("/oauth2/token", server.takeRequest().getPath());
        assertEquals("ka10082", server.takeRequest().getHeader("api-id"));
        assertEquals("ka10083", server.takeRequest().getHeader("api-id"));
        assertEquals("ka10094", server.takeRequest().getHeader("api-id"));
        assertEquals("ka10081", server.takeRequest().getHeader("api-id"));
    }

    @Test
    @DisplayName("종목명과 시장으로 종목 목록을 검색한다")
    void searchesStockCatalogByNameAndMarket() {
        enqueueJson(
                200,
                """
                {"return_code":0,"token":"access-token","expires_dt":"20991231235959"}
                """);
        enqueueJson(
                200,
                """
                {"return_code":0,"list":[{"code":"005930","name":"삼성전자"}]}
                """);
        enqueueJson(
                200,
                """
                {"return_code":0,"list":[{"code":"035720","name":"카카오"}]}
                """);

        List<StockSearchResult> result = service.searchStocks("삼성", "KOSPI").block();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("005930", result.getFirst().code());
        assertEquals("KOSPI", result.getFirst().market());
        assertEquals("005930", service.searchStocks("삼성 전자", "ALL").block().getFirst().code());
        assertEquals("005930", service.searchStocks("ㅅㅅㅈㅈ", "ALL").block().getFirst().code());
    }

    @Test
    @DisplayName("상품유형 이름과 필터로 종목 목록을 검색한다")
    void searchesStockCatalogByProductType() {
        enqueueJson(
                200,
                """
                {"return_code":0,"token":"access-token","expires_dt":"20991231235959"}
                """);
        enqueueJson(
                200,
                """
                {"return_code":0,"list":[
                  {"code":"005930","name":"삼성전자"},
                  {"code":"069500","name":"KODEX 200"}
                ]}
                """);
        enqueueJson(200, "{\"return_code\":0,\"list\":[]}");

        List<StockSearchResult> byKeyword = service.searchStocks("ETF", "ALL", "ALL").block();
        List<StockSearchResult> byFilter = service.searchStocks("200", "ALL", "ETF").block();

        assertNotNull(byKeyword);
        assertEquals("069500", byKeyword.getFirst().code());
        assertEquals("ETF", byKeyword.getFirst().productTypeLabel());
        assertEquals(1, byFilter.size());
        assertEquals(0, service.searchStocks("삼성", "ALL", "ETF").block().size());
    }

    @Test
    @DisplayName("키움 종목 없음 응답을 전용 오류 코드로 분류한다")
    void classifiesStockNotFoundResponse() {
        enqueueJson(
                200,
                """
                {"return_code":0,"token":"access-token","expires_dt":"20991231235959"}
                """);
        enqueueJson(
                200,
                """
                {"return_code":100,"return_msg":"종목 없음"}
                """);

        KiwoomApiException error =
                assertThrows(
                        KiwoomApiException.class,
                        () -> service.getStockCurrentPrice("005930").block());

        assertEquals(KiwoomErrorCode.STOCK_NOT_FOUND, error.errorCode());
        assertEquals(100, error.upstreamCode());
    }

    @Test
    @DisplayName("키움 장 운영시간 응답을 전용 오류 코드로 분류한다")
    void classifiesMarketClosedResponse() {
        enqueueJson(
                200,
                """
                {"return_code":0,"token":"access-token","expires_dt":"20991231235959"}
                """);
        enqueueJson(
                200,
                """
                {"return_code":200,"return_msg":"장 운영시간이 아닙니다"}
                """);

        KiwoomApiException error =
                assertThrows(
                        KiwoomApiException.class,
                        () -> service.getStockCurrentPrice("005930").block());

        assertEquals(KiwoomErrorCode.MARKET_CLOSED, error.errorCode());
    }

    @Test
    @DisplayName("401 응답이면 토큰을 갱신하고 한 번 재시도한다")
    void refreshesTokenAfterUnauthorized() throws InterruptedException {
        enqueueJson(
                200,
                """
                {"return_code":0,"token":"expired-token","expires_dt":"20991231235959"}
                """);
        enqueueJson(401, "unauthorized");
        enqueueJson(
                200,
                """
                {"return_code":0,"token":"new-token","expires_dt":"20991231235959"}
                """);
        enqueueJson(
                200,
                """
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
        enqueueJson(
                200,
                """
                {"return_code":0,"token":"old-token","expires_dt":"20200101000000"}
                """);
        enqueueJson(
                200,
                """
                {"return_code":0,"stk_cd":"005930","cur_prc":"70000"}
                """);
        enqueueJson(
                200,
                """
                {"return_code":0,"token":"new-token","expires_dt":"20991231235959"}
                """);
        enqueueJson(
                200,
                """
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

        RuntimeException error =
                assertThrows(
                        RuntimeException.class,
                        () -> service.getStockCurrentPrice("005930").block());

        assertTrue(error.getMessage().contains("일시적인 키움 API 오류"));
    }

    @Test
    @DisplayName("429 응답이면 지수 백오프로 재시도한다")
    void retriesRateLimitResponse() throws InterruptedException {
        enqueueJson(
                200,
                """
                {"return_code":0,"token":"access-token","expires_dt":"20991231235959"}
                """);
        enqueueJson(429, "rate limited");
        enqueueJson(
                200,
                """
                {"return_code":0,"stk_cd":"005930","cur_prc":"72000"}
                """);

        StockPriceResponse response = service.getStockCurrentPrice("005930").block();

        assertNotNull(response);
        assertEquals("72000", response.getCurrentPrice());
        assertEquals("/oauth2/token", server.takeRequest().getPath());
        assertEquals("/api/dostk/stkinfo", server.takeRequest().getPath());
        assertEquals("/api/dostk/stkinfo", server.takeRequest().getPath());
        assertEquals(
                1,
                meterRegistry
                        .get("kiwoom.api.retries")
                        .tag("reason", "rate_limited")
                        .counter()
                        .count());
    }

    @Test
    @DisplayName("잘못된 토큰 JSON을 예외로 전달한다")
    void rejectsMalformedJson() {
        enqueueJson(200, "not-json");

        RuntimeException error =
                assertThrows(
                        RuntimeException.class,
                        () -> service.getStockCurrentPrice("005930").block());

        assertTrue(error.getMessage().contains("토큰 응답 JSON 파싱 실패"));
    }

    @Test
    @DisplayName("검색 결과는 코드 정확일치 우선으로 정렬된다")
    void searchResultsSortedByCodeExactMatchFirst() {
        enqueueJson(
                200,
                """
                {"return_code":0,"token":"access-token","expires_dt":"20991231235959"}
                """);
        enqueueJson(
                200,
                """
                {"return_code":0,"list":[
                  {"code":"005930","name":"삼성전자우"},
                  {"code":"005935","name":"삼성전자"},
                  {"code":"035720","name":"카카오"}
                ]}
                """);
        enqueueJson(
                200,
                """
                {"return_code":0,"list":[]}
                """);

        // "005935" 검색 시 코드 정확일치(005935)가 종목명 포함(삼성전자)보다 먼저
        List<StockSearchResult> result = service.searchStocks("005935", "ALL").block();
        assertEquals("005935", result.getFirst().code());

        // "삼성" 검색 시 종목명 접두사 매칭
        List<StockSearchResult> nameResult = service.searchStocks("삼성", "ALL").block();
        assertTrue(nameResult.size() >= 2);
    }

    private void enqueueJson(int status, String body) {
        server.enqueue(
                new MockResponse()
                        .setResponseCode(status)
                        .setHeader("Content-Type", "application/json")
                        .setBody(body));
    }

    private KiwoomApiService createServiceWithCache(Duration cacheTtl) {
        KiwoomApiProperties properties = properties(cacheTtl);
        ObjectMapper objectMapper = new ObjectMapper();
        return new KiwoomApiService(
                new KiwoomHttpClient(WebClient.create(), properties, meterRegistry),
                new KiwoomResponseMapper(objectMapper),
                properties,
                meterRegistry,
                new TechnicalIndicatorService());
    }
}
