package com.example.kiwoom.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.kiwoom.config.KiwoomApiProperties;
import com.example.kiwoom.config.WebClientConfig;
import com.example.kiwoom.error.KiwoomErrorCode;
import com.example.kiwoom.error.RetryableKiwoomException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KiwoomHttpClientTest {
    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void retriesUntilServerErrorAttemptsAreExhausted() {
        for (int attempt = 0; attempt < 3; attempt++) {
            server.enqueue(new MockResponse().setResponseCode(503));
        }

        RetryableKiwoomException error =
                assertThrows(
                        RetryableKiwoomException.class,
                        () ->
                                client(Duration.ofSeconds(1))
                                        .requestCurrentPrice("005930", "token")
                                        .block());

        assertEquals(KiwoomErrorCode.UPSTREAM_UNAVAILABLE, error.errorCode());
        assertEquals(3, server.getRequestCount());
    }

    @Test
    void retriesWhenServerClosesConnectionDuringResponseBody() {
        for (int attempt = 0; attempt < 3; attempt++) {
            server.enqueue(
                    new MockResponse()
                            .setResponseCode(200)
                            .setBody("{\"return_code\":0")
                            .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY));
        }

        RetryableKiwoomException error =
                assertThrows(
                        RetryableKiwoomException.class,
                        () ->
                                client(Duration.ofSeconds(1))
                                        .requestCurrentPrice("005930", "token")
                                        .block());

        assertEquals(KiwoomErrorCode.UPSTREAM_UNAVAILABLE, error.errorCode());
        assertEquals(3, server.getRequestCount());
    }

    @Test
    void acceptsLargeStockListResponsesWithinMemoryLimit() {
        StringBuilder body = new StringBuilder("{\"return_code\":0,\"list\":[");
        for (int i = 0; i < 15000; i++) {
            if (i > 0) body.append(',');
            body.append("{\"code\":\"")
                    .append(String.format("%06d", i))
                    .append("\",\"name\":\"테스트종목")
                    .append(i)
                    .append("\"}");
        }
        body.append("]}");
        server.enqueue(new MockResponse().setResponseCode(200).setBody(body.toString()));

        String response = client(Duration.ofSeconds(1)).requestStockList("0", "token").block();

        assertTrue(response.contains("\"name\":\"테스트종목14999\""));
    }

    @Test
    void classifiesResponseTimeoutAsRetryableAndExhaustsRetries() {
        for (int attempt = 0; attempt < 3; attempt++) {
            server.enqueue(
                    new MockResponse()
                            .setHeadersDelay(300, TimeUnit.MILLISECONDS)
                            .setBody("{\"return_code\":0}"));
        }

        RetryableKiwoomException error =
                assertThrows(
                        RetryableKiwoomException.class,
                        () ->
                                client(Duration.ofMillis(50))
                                        .requestCurrentPrice("005930", "token")
                                        .block());

        assertEquals(KiwoomErrorCode.UPSTREAM_UNAVAILABLE, error.errorCode());
        assertEquals(3, server.getRequestCount());
    }

    @Test
    void sendsSupportedMarketCodesToRankingApis() throws InterruptedException {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"return_code\":0}"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"return_code\":0}"));
        KiwoomHttpClient client = client(Duration.ofSeconds(1));

        client.requestChangeRateRanking("001", "1", "token").block();
        client.requestVolumeRanking("101", "token").block();

        var changeRequest = server.takeRequest();
        var volumeRequest = server.takeRequest();
        assertEquals("ka10027", changeRequest.getHeader("api-id"));
        assertTrue(changeRequest.getBody().readUtf8().contains("\"mrkt_tp\":\"001\""));
        assertEquals("ka10030", volumeRequest.getHeader("api-id"));
        assertTrue(volumeRequest.getBody().readUtf8().contains("\"mrkt_tp\":\"101\""));
    }

    @Test
    void sendsAccountPortfolioRequests() throws InterruptedException {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"return_code\":0}"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"return_code\":0}"));
        KiwoomHttpClient client = client(Duration.ofSeconds(1));

        client.requestAccountNumber("token").block();
        client.requestAccountPortfolio("token").block();

        var numberRequest = server.takeRequest();
        var portfolioRequest = server.takeRequest();
        assertEquals("/api/dostk/acnt", numberRequest.getPath());
        assertEquals("ka00001", numberRequest.getHeader("api-id"));
        assertEquals("kt00018", portfolioRequest.getHeader("api-id"));
        String body = portfolioRequest.getBody().readUtf8();
        assertTrue(body.contains("\"qry_tp\":\"1\""));
        assertTrue(body.contains("\"dmst_stex_tp\":\"KRX\""));
    }

    private KiwoomHttpClient client(Duration responseTimeout) {
        KiwoomApiProperties properties =
                new KiwoomApiProperties(
                        server.url("/").toString(),
                        "key",
                        "secret",
                        Duration.ofSeconds(1),
                        responseTimeout,
                        5,
                        2,
                        Duration.ofMillis(1),
                        Duration.ZERO,
                        Duration.ZERO);
        return new KiwoomHttpClient(
                new WebClientConfig().webClient(properties), properties, new SimpleMeterRegistry());
    }
}
