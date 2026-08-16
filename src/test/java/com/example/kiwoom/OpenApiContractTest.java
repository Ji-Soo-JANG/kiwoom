package com.example.kiwoom;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
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
            "spring.r2dbc.url=r2dbc:h2:mem:///openapi-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.url=jdbc:h2:mem:openapi-flyway-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.username=sa",
            "spring.flyway.enabled=false"
        })
class OpenApiContractTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired private WebTestClient webTestClient;

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void committedContractContainsEveryRuntimeApiOperation() throws IOException {
        JsonNode committed = OBJECT_MAPPER.readTree(Files.readString(Path.of("docs/openapi.json")));
        byte[] runtimeBody =
                webTestClient
                        .get()
                        .uri("/v3/api-docs")
                        .exchange()
                        .expectStatus()
                        .isOk()
                        .expectBody()
                        .returnResult()
                        .getResponseBody();
        JsonNode runtime = OBJECT_MAPPER.readTree(runtimeBody);

        Iterator<Map.Entry<String, JsonNode>> paths = runtime.path("paths").fields();
        while (paths.hasNext()) {
            Map.Entry<String, JsonNode> path = paths.next();
            if (!path.getKey().startsWith("/api/")) {
                continue;
            }
            assertThat(committed.path("paths").has(path.getKey()))
                    .as("committed path %s", path.getKey())
                    .isTrue();
            path.getValue()
                    .fieldNames()
                    .forEachRemaining(
                            method ->
                                    assertThat(
                                                    committed
                                                            .path("paths")
                                                            .path(path.getKey())
                                                            .has(method))
                                            .as("committed operation %s %s", method, path.getKey())
                                            .isTrue());
        }
    }
}
