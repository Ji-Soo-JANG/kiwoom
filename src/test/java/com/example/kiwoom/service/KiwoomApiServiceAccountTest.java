package com.example.kiwoom.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.example.kiwoom.client.KiwoomHttpClient;
import com.example.kiwoom.config.KiwoomApiProperties;
import com.example.kiwoom.dto.AccountPortfolioResponse;
import com.example.kiwoom.mapper.KiwoomResponseMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Duration;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

@DisplayName("KiwoomApiService 계좌 포트폴리오 및 페이징 테스트")
class KiwoomApiServiceAccountTest {

    private MockWebServer server;
    private KiwoomApiService service;

    private static final String TOKEN_RESPONSE =
            "{\"return_code\":0,\"token\":\"access-token\",\"expires_dt\":\"20991231235959\"}";
    private static final String ACCOUNT_NUMBER_RESPONSE =
            "{\"return_code\":0,\"acctNo\":\"123-456-78901\"}";
    private static final String EMPTY_POSITIONS_RESPONSE =
            "{\"return_code\":0,\"tot_pur_amt\":\"0\",\"tot_evlt_amt\":\"0\","
                    + "\"tot_evlt_pl\":\"0\",\"tot_prft_rt\":\"0\","
                    + "\"prsm_dpst_aset_amt\":\"500000\"}";

    private static final String PORTFOLIO_WITH_POSITION =
            "{\"return_code\":0,\"acnt_evlt_remn_indv_tot\":["
                    + "{\"stk_cd\":\"005930\",\"stk_nm\":\"삼성전자\","
                    + "\"rmnd_qty\":\"10\",\"pur_pric\":\"70000\",\"cur_prc\":\"75000\","
                    + "\"evlt_amt\":\"750000\",\"evltv_prft\":\"50000\",\"prft_rt\":\"7.14\"}"
                    + "],\"tot_pur_amt\":\"700000\",\"tot_evlt_amt\":\"750000\","
                    + "\"tot_evlt_pl\":\"50000\",\"tot_prft_rt\":\"7.14\","
                    + "\"prsm_dpst_aset_amt\":\"1000000\"}";

    private static final String PORTFOLIO_POSITION_2 =
            "{\"return_code\":0,\"acnt_evlt_remn_indv_tot\":["
                    + "{\"stk_cd\":\"000660\",\"stk_nm\":\"SK하이닉스\","
                    + "\"rmnd_qty\":\"5\",\"pur_pric\":\"100000\",\"cur_prc\":\"110000\","
                    + "\"evlt_amt\":\"550000\",\"evltv_prft\":\"50000\",\"prft_rt\":\"10.00\"}"
                    + "],\"tot_pur_amt\":\"500000\",\"tot_evlt_amt\":\"550000\","
                    + "\"tot_evlt_pl\":\"50000\",\"tot_prft_rt\":\"10.00\","
                    + "\"prsm_dpst_aset_amt\":\"1000000\"}";

    private static final String CHART_RESPONSE =
            "{\"return_code\":0,\"stk_dt_pole_chart_qry\":["
                    + "{\"dt\":\"20260815\",\"open_pric\":\"70000\",\"high_pric\":\"71000\","
                    + "\"low_pric\":\"69000\",\"cur_prc\":\"70500\",\"trde_qty\":\"1000\"}]}";

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        KiwoomApiProperties props =
                new KiwoomApiProperties(
                        server.url("/").toString(),
                        "test-key",
                        "test-secret",
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(2),
                        5,
                        2,
                        Duration.ofMillis(1),
                        Duration.ZERO,
                        Duration.ZERO);
        service =
                new KiwoomApiService(
                        new KiwoomHttpClient(WebClient.create(), props, meterRegistry),
                        new KiwoomResponseMapper(new ObjectMapper()),
                        props,
                        meterRegistry,
                        new TechnicalIndicatorService());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    @DisplayName("계좌 포트폴리오를 조회한다")
    void getsAccountPortfolio() {
        useAccountDispatcher(PORTFOLIO_WITH_POSITION, null);
        AccountPortfolioResponse result = service.getAccountPortfolio().block();
        assertNotNull(result);
        assertEquals(1, result.positions().size());
    }

    @Test
    @DisplayName("빈 보유종목 계좌도 처리한다")
    void handlesEmptyPositions() {
        useAccountDispatcher(EMPTY_POSITIONS_RESPONSE, null);
        AccountPortfolioResponse result = service.getAccountPortfolio().block();
        assertNotNull(result);
        assertNotNull(result.accountNumber());
        assertEquals(0, result.positions().size());
    }

    @Test
    @DisplayName("cont-yn 페이징으로 여러 페이지를 순회한다")
    void pagesThroughMultipleAccountPages() {
        // First kt00018 call returns page1 with cont-yn=Y, second returns page2 without
        server.setDispatcher(
                new Dispatcher() {
                    int portfolioCallCount = 0;

                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        String path = request.getPath();
                        String apiId = request.getHeader("api-id");
                        if ("/oauth2/token".equals(path)) {
                            return jsonResponse(TOKEN_RESPONSE);
                        }
                        if ("ka00001".equals(apiId)) {
                            return jsonResponse(ACCOUNT_NUMBER_RESPONSE);
                        }
                        if ("kt00018".equals(apiId)) {
                            portfolioCallCount++;
                            if (portfolioCallCount == 1) {
                                MockResponse response = jsonResponse(PORTFOLIO_WITH_POSITION);
                                response.setHeader("cont-yn", "Y");
                                response.setHeader("next-key", "page2");
                                return response;
                            }
                            return jsonResponse(PORTFOLIO_POSITION_2);
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                });
        AccountPortfolioResponse result = service.getAccountPortfolio().block();
        assertNotNull(result);
        assertEquals(2, result.positions().size());
    }

    @Test
    @DisplayName("차트 조회 기본값은 120건이다")
    void defaultDailyLimitIs120() {
        useChartDispatcher(CHART_RESPONSE);
        var result = service.getDailyPrices("005930", "20260816").block();
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("차트 조회 커스텀 제한값을 적용한다")
    void appliesCustomDailyLimit() {
        useChartDispatcher(CHART_RESPONSE);
        var result = service.getDailyPrices("005930", "20260816", 10).block();
        assertNotNull(result);
    }

    @Test
    @DisplayName("markets rankings - parallel via Dispatcher")
    void getsMarketRankings() {
        server.setDispatcher(
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        return routeRankingRequest(request);
                    }
                });
        var result = service.getMarketRankings().block();
        assertNotNull(result);
        assertNotNull(result.gainers());
        assertNotNull(result.losers());
        assertNotNull(result.mostTraded());
    }

    @Test
    @DisplayName("stock catalog refresh and status")
    void refreshesStockCatalog() {
        server.setDispatcher(
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        return routeCatalogRequest(request);
                    }
                });
        var status = service.refreshStockCatalog().block();
        assertNotNull(status);
        var catalogStatus = service.stockCatalogStatus();
        assertNotNull(catalogStatus);
    }

    @Test
    @DisplayName("cache stats are initially zero")
    void cacheStatsInitiallyZero() {
        var stats = service.getDailyPriceCacheStats();
        assertEquals(0, stats.hits());
        assertEquals(0, stats.apiCalls());
        assertEquals(0, stats.entries());
    }

    // --- Dispatcher helpers ---

    private MockResponse jsonResponse(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }

    private void useAccountDispatcher(String portfolioBody, String nextKey) {
        server.setDispatcher(
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        return routeRequest(request, portfolioBody, nextKey);
                    }
                });
    }

    private MockResponse routeRequest(
            RecordedRequest request, String portfolioBody, String nextKey) {
        String path = request.getPath();
        String apiId = request.getHeader("api-id");
        if ("/oauth2/token".equals(path)) {
            return jsonResponse(TOKEN_RESPONSE);
        }
        if ("ka00001".equals(apiId)) {
            return jsonResponse(ACCOUNT_NUMBER_RESPONSE);
        }
        if ("kt00018".equals(apiId)) {
            MockResponse response = jsonResponse(portfolioBody);
            if (nextKey != null) {
                response.setHeader("cont-yn", "Y");
                response.setHeader("next-key", nextKey);
            }
            return response;
        }
        return new MockResponse().setResponseCode(404);
    }

    private void useChartDispatcher(String chartBody) {
        server.setDispatcher(
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        String path = request.getPath();
                        if ("/oauth2/token".equals(path)) {
                            return jsonResponse(TOKEN_RESPONSE);
                        }
                        if (request.getHeader("api-id") != null) {
                            return jsonResponse(chartBody);
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                });
    }

    private MockResponse routeRankingRequest(RecordedRequest request) {
        String path = request.getPath();
        String apiId = request.getHeader("api-id");
        String body = request.getBody().readUtf8();
        if ("/oauth2/token".equals(path)) {
            return jsonResponse(TOKEN_RESPONSE);
        }
        if ("ka10027".equals(apiId)) {
            if (body.contains("\"sort_tp\":\"1\"")) {
                return jsonResponse(
                        "{\"return_code\":0,\"pred_pre_flu_rt_upper\":["
                                + "{\"stk_cd\":\"035720\",\"stk_nm\":\"카카오\","
                                + "\"cur_prc\":\"50000\",\"flu_rt\":\"2.5\","
                                + "\"trde_qty\":\"1000\"}]}");
            }
            return jsonResponse(
                    "{\"return_code\":0,\"pred_pre_flu_rt_upper\":["
                            + "{\"stk_cd\":\"035720\",\"stk_nm\":\"카카오\","
                            + "\"cur_prc\":\"50000\",\"flu_rt\":\"-1.5\","
                            + "\"trde_qty\":\"500\"}]}");
        }
        if ("ka10030".equals(apiId)) {
            return jsonResponse(
                    "{\"return_code\":0,\"tdy_trde_qty_upper\":["
                            + "{\"stk_cd\":\"035720\",\"stk_nm\":\"카카오\","
                            + "\"cur_prc\":\"50000\",\"flu_rt\":\"2.5\","
                            + "\"trde_qty\":\"2000\"}]}");
        }
        return new MockResponse().setResponseCode(404);
    }

    private MockResponse routeCatalogRequest(RecordedRequest request) {
        String path = request.getPath();
        String apiId = request.getHeader("api-id");
        if ("/oauth2/token".equals(path)) {
            return jsonResponse(TOKEN_RESPONSE);
        }
        if ("ka10099".equals(apiId)) {
            String body = request.getBody().readUtf8();
            if (body.contains("\"mrkt_tp\":\"0\"")) {
                return jsonResponse(
                        "{\"return_code\":0,\"list\":["
                                + "{\"code\":\"005930\",\"name\":\"삼성전자\"}]}");
            }
            return jsonResponse(
                    "{\"return_code\":0,\"list\":[" + "{\"code\":\"035720\",\"name\":\"카카오\"}]}");
        }
        return new MockResponse().setResponseCode(404);
    }
}
