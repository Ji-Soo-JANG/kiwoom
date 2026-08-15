package com.example.kiwoom;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
        "kiwoom.api.base-url=http://localhost",
        "kiwoom.api.key=test-key",
        "kiwoom.api.secret=test-secret",
        "kiwoom.api.connect-timeout=1s",
        "kiwoom.api.response-timeout=2s",
        "kiwoom.api.max-connections=5",
        "kiwoom.api.max-retries=2",
        "kiwoom.api.retry-backoff=1ms"
        }
)
class KiwoomApplicationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void contextLoads() {
    }

    @Test
    void exposesOpenApiDocument() {
        webTestClient.get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.info.title").isEqualTo("Kiwoom Stock API")
                .jsonPath("$.paths['/api/kiwoom/stock-price/{code}']").exists();
    }

    @Test
    void managesWatchlist() {
        webTestClient.post().uri("/api/watchlist")
                .bodyValue("{\"code\":\"005930\"}")
                .header("Content-Type", "application/json")
                .exchange().expectStatus().isCreated();

        webTestClient.get().uri("/api/watchlist")
                .exchange().expectStatus().isOk()
                .expectBody().json("[\"005930\"]");

        webTestClient.delete().uri("/api/watchlist/005930")
                .exchange().expectStatus().isNoContent();
    }
}
