package com.example.kiwoom;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.kiwoom.dto.AlertRule;
import com.example.kiwoom.repository.WatchlistRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
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
            "kiwoom.api.retry-backoff=1ms",
            "kiwoom.api.current-price-cache-ttl=3s",
            "kiwoom.api.daily-price-cache-ttl=10m",
            "spring.r2dbc.url=r2dbc:h2:mem:///kiwoom-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.url=jdbc:h2:mem:flyway-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.username=sa",
            "spring.sql.init.mode=always",
            "spring.flyway.enabled=false"
        })
class KiwoomApplicationTest {

    @Autowired private WebTestClient webTestClient;

    @Autowired private WatchlistRepository watchlistRepository;

    @Test
    void contextLoads() {}

    @Test
    void rejectsUnauthenticatedPortfolioRequest() {
        webTestClient.get().uri("/api/portfolio").exchange().expectStatus().isUnauthorized();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void returnsTraceIdInHeaderAndErrorBody() {
        webTestClient
                .get()
                .uri("/api/kiwoom/stock-price/invalid")
                .header("X-Trace-Id", "test-trace-1234")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectHeader()
                .valueEquals("X-Trace-Id", "test-trace-1234")
                .expectBody()
                .jsonPath("$.traceId")
                .isEqualTo("test-trace-1234");
    }

    @Test
    void isolatesWatchlistsByAuthenticatedUser() {
        watchlistRepository.add("alice", "035420").block();

        assertThat(watchlistRepository.findAll("alice").collectList().block())
                .containsExactly("035420");
        assertThat(watchlistRepository.findAll("bob").collectList().block()).isEmpty();

        watchlistRepository.remove("alice", "035420").block();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void exposesOpenApiDocument() {
        webTestClient
                .get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.info.title")
                .isEqualTo("Kiwoom Stock API")
                .jsonPath("$.paths['/api/kiwoom/stock-price/{code}']")
                .exists();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void exposesPrometheusMetricsToAdmin() {
        webTestClient
                .get()
                .uri("/actuator/prometheus")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("jvm_memory_used_bytes"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void managesWatchlist() {
        webTestClient
                .post()
                .uri("/api/watchlist")
                .bodyValue("{\"code\":\"005930\"}")
                .header("Content-Type", "application/json")
                .exchange()
                .expectStatus()
                .isCreated();

        webTestClient
                .get()
                .uri("/api/watchlist")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .json("[\"005930\"]");

        webTestClient.delete().uri("/api/watchlist/005930").exchange().expectStatus().isNoContent();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void managesPriceAlertRules() {
        AlertRule created =
                webTestClient
                        .post()
                        .uri("/api/alerts/rules")
                        .bodyValue(
                                "{\"code\":\"005930\",\"conditionType\":\"PRICE_ABOVE\",\"threshold\":80000}")
                        .header("Content-Type", "application/json")
                        .exchange()
                        .expectStatus()
                        .isCreated()
                        .expectBody(AlertRule.class)
                        .returnResult()
                        .getResponseBody();

        assertThat(created).isNotNull();
        webTestClient
                .get()
                .uri("/api/alerts/rules")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].code")
                .isEqualTo("005930");

        webTestClient
                .patch()
                .uri("/api/alerts/rules/{id}", created.id())
                .bodyValue("{\"threshold\":81000,\"enabled\":false}")
                .header("Content-Type", "application/json")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.threshold")
                .isEqualTo(81000)
                .jsonPath("$.enabled")
                .isEqualTo(false);

        webTestClient
                .delete()
                .uri("/api/alerts/rules/{id}", created.id())
                .exchange()
                .expectStatus()
                .isNoContent();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void managesMacdAlertRuleWithoutThreshold() {
        AlertRule created =
                webTestClient
                        .post()
                        .uri("/api/alerts/rules")
                        .bodyValue(
                                "{\"code\":\"000660\",\"conditionType\":\"MACD_CROSS_UP\",\"threshold\":null}")
                        .header("Content-Type", "application/json")
                        .exchange()
                        .expectStatus()
                        .isCreated()
                        .expectBody(AlertRule.class)
                        .returnResult()
                        .getResponseBody();

        assertThat(created).isNotNull();
        assertThat(created.threshold()).isNull();
        webTestClient
                .delete()
                .uri("/api/alerts/rules/{id}", created.id())
                .exchange()
                .expectStatus()
                .isNoContent();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void validatesChangeRateAlertRuleThreshold() {
        AlertRule created =
                webTestClient
                        .post()
                        .uri("/api/alerts/rules")
                        .bodyValue(
                                "{\"code\":\"035420\",\"conditionType\":\"CHANGE_RATE_ABOVE\",\"threshold\":5}")
                        .header("Content-Type", "application/json")
                        .exchange()
                        .expectStatus()
                        .isCreated()
                        .expectBody(AlertRule.class)
                        .returnResult()
                        .getResponseBody();

        assertThat(created).isNotNull();
        assertThat(created.threshold()).isEqualByComparingTo("5");
        webTestClient
                .post()
                .uri("/api/alerts/rules")
                .bodyValue(
                        "{\"code\":\"035420\",\"conditionType\":\"CHANGE_RATE_BELOW\",\"threshold\":101}")
                .header("Content-Type", "application/json")
                .exchange()
                .expectStatus()
                .isBadRequest();
        webTestClient
                .delete()
                .uri("/api/alerts/rules/{id}", created.id())
                .exchange()
                .expectStatus()
                .isNoContent();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void managesPortfolioPositions() {
        webTestClient
                .put()
                .uri("/api/portfolio/005930")
                .bodyValue("{\"quantity\":10,\"averagePrice\":70000}")
                .header("Content-Type", "application/json")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo("005930")
                .jsonPath("$.quantity")
                .isEqualTo(10)
                .jsonPath("$.averagePrice")
                .isEqualTo(70000);

        webTestClient
                .get()
                .uri("/api/portfolio")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].code")
                .isEqualTo("005930");

        webTestClient.delete().uri("/api/portfolio/005930").exchange().expectStatus().isNoContent();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void recordsTradesAndCalculatesPositionAndRealizedProfit() {
        webTestClient
                .post()
                .uri("/api/portfolio/transactions")
                .bodyValue(
                        "{\"code\":\"000660\",\"type\":\"BUY\",\"quantity\":10,\"price\":100000,\"fee\":1000}")
                .header("Content-Type", "application/json")
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.id")
                .isNumber()
                .jsonPath("$.realizedProfitLoss")
                .isEqualTo(0);

        webTestClient
                .post()
                .uri("/api/portfolio/transactions")
                .bodyValue(
                        "{\"code\":\"000660\",\"type\":\"SELL\",\"quantity\":4,\"price\":110000,\"fee\":500,\"tax\":300}")
                .header("Content-Type", "application/json")
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.realizedProfitLoss")
                .isEqualTo(38800);

        webTestClient
                .get()
                .uri("/api/portfolio")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].code")
                .isEqualTo("000660")
                .jsonPath("$[0].quantity")
                .isEqualTo(6)
                .jsonPath("$[0].averagePrice")
                .isEqualTo(100100);

        webTestClient
                .get()
                .uri("/api/portfolio/transactions")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.content.length()")
                .isEqualTo(2);

        webTestClient.delete().uri("/api/portfolio/000660").exchange().expectStatus().isNoContent();
    }

    @Test
    void servesReactEntryPointForClientRoutes() {
        webTestClient
                .get()
                .uri("/portfolio")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentTypeCompatibleWith("text/html")
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("id=\"root\""));
    }

    @Test
    void servesLoginPage() {
        webTestClient
                .get()
                .uri("/login")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentTypeCompatibleWith("text/html")
                .expectBody(String.class)
                .value(
                        body ->
                                assertThat(body)
                                        .contains("name=\"username\"")
                                        .contains("name=\"password\""));
    }

    @Test
    @WithMockUser(username = "local-user")
    void returnsCurrentUser() {
        webTestClient
                .get()
                .uri("/api/auth/me")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.username")
                .isEqualTo("local-user");
    }
}
