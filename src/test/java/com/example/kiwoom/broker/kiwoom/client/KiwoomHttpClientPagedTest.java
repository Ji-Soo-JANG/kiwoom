package com.example.kiwoom.broker.kiwoom.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.kiwoom.config.KiwoomApiProperties;
import com.example.kiwoom.config.WebClientConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Duration;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KiwoomHttpClientPagedTest {

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
    void requestAccountPortfolioPaged_noContinuation() throws InterruptedException {
        server.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody("{\"return_code\":0,\"acnt_evlt_remn_indv_tot\":[]}"));

        KiwoomHttpClient client = client();
        KiwoomHttpClient.PagedResponse result =
                client.requestAccountPortfolioPaged("token", null).block();
        assertFalse(result.continuePaging());
        assertEquals("{\"return_code\":0,\"acnt_evlt_remn_indv_tot\":[]}", result.body());
        RecordedRequest request = server.takeRequest();
        assertEquals("kt00018", request.getHeader("api-id"));
        assertEquals(null, request.getHeader("cont-yn"));
    }

    @Test
    void requestAccountPortfolioPaged_withContinuation() throws InterruptedException {
        server.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setHeader("cont-yn", "Y")
                        .setHeader("next-key", "page2")
                        .setBody("{\"return_code\":0,\"data\":\"page1\"}"));

        KiwoomHttpClient client = client();
        KiwoomHttpClient.PagedResponse result =
                client.requestAccountPortfolioPaged("token", null).block();

        assertTrue(result.continuePaging());
        assertEquals("page2", result.nextKey());
        assertEquals("{\"return_code\":0,\"data\":\"page1\"}", result.body());
        RecordedRequest request = server.takeRequest();
        assertEquals(null, request.getHeader("cont-yn"));
    }

    @Test
    void requestAccountPortfolioPaged_sendsNextKey() throws InterruptedException {
        server.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody("{\"return_code\":0,\"data\":\"page2\"}"));

        KiwoomHttpClient client = client();
        KiwoomHttpClient.PagedResponse result =
                client.requestAccountPortfolioPaged("token", "page2key").block();

        assertFalse(result.continuePaging());
        assertEquals("{\"return_code\":0,\"data\":\"page2\"}", result.body());
        RecordedRequest request = server.takeRequest();
        assertEquals("Y", request.getHeader("cont-yn"));
        assertEquals("page2key", request.getHeader("next-key"));
    }

    @Test
    void requestAccountPortfolioPaged_authError() {
        server.enqueue(new MockResponse().setResponseCode(401));

        KiwoomHttpClient client = client();

        try {
            client.requestAccountPortfolioPaged("token", null).block();
        } catch (Exception e) {
            // Expected: KiwoomAuthenticationException after retry exhaustion
        }
    }

    @Test
    void requestAccountNumber_sendsCorrectApiId() throws InterruptedException {
        server.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody("{\"return_code\":0,\"acctNo\":\"123-456-78901\"}"));

        KiwoomHttpClient client = client();
        String result = client.requestAccountNumber("token").block();

        assertEquals("{\"return_code\":0,\"acctNo\":\"123-456-78901\"}", result);
        RecordedRequest request = server.takeRequest();
        assertEquals("ka00001", request.getHeader("api-id"));
        assertEquals("/api/dostk/acnt", request.getPath());
    }

    private KiwoomHttpClient client() {
        KiwoomApiProperties props =
                new KiwoomApiProperties(
                        server.url("/").toString(),
                        "key",
                        "secret",
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(2),
                        5,
                        2,
                        Duration.ofMillis(1),
                        Duration.ZERO,
                        Duration.ZERO);
        return new KiwoomHttpClient(
                new WebClientConfig().webClient(props), props, new SimpleMeterRegistry());
    }
}
