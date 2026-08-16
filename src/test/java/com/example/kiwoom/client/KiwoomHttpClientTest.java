package com.example.kiwoom.client;

import com.example.kiwoom.config.KiwoomApiProperties;
import com.example.kiwoom.config.WebClientConfig;
import com.example.kiwoom.error.KiwoomErrorCode;
import com.example.kiwoom.error.RetryableKiwoomException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

        RetryableKiwoomException error = assertThrows(RetryableKiwoomException.class,
                () -> client(Duration.ofSeconds(1)).requestCurrentPrice("005930", "token").block());

        assertEquals(KiwoomErrorCode.UPSTREAM_UNAVAILABLE, error.errorCode());
        assertEquals(3, server.getRequestCount());
    }

    @Test
    void classifiesResponseTimeoutAsRetryableAndExhaustsRetries() {
        for (int attempt = 0; attempt < 3; attempt++) {
            server.enqueue(new MockResponse().setHeadersDelay(300, TimeUnit.MILLISECONDS)
                    .setBody("{\"return_code\":0}"));
        }

        RetryableKiwoomException error = assertThrows(RetryableKiwoomException.class,
                () -> client(Duration.ofMillis(50)).requestCurrentPrice("005930", "token").block());

        assertEquals(KiwoomErrorCode.UPSTREAM_UNAVAILABLE, error.errorCode());
        assertEquals(3, server.getRequestCount());
    }

    private KiwoomHttpClient client(Duration responseTimeout) {
        KiwoomApiProperties properties = new KiwoomApiProperties(server.url("/").toString(),
                "key", "secret", Duration.ofSeconds(1), responseTimeout, 5, 2,
                Duration.ofMillis(1), Duration.ZERO, Duration.ZERO);
        return new KiwoomHttpClient(new WebClientConfig().webClient(properties), properties,
                new SimpleMeterRegistry());
    }
}
