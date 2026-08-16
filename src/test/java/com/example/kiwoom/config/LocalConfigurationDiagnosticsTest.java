package com.example.kiwoom.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

class LocalConfigurationDiagnosticsTest {

    @Test
    void acceptsCompleteLocalConfiguration() {
        assertThatCode(
                        () ->
                                diagnostics(
                                                properties("key", "secret"),
                                                "r2dbc:postgresql://db/app?ssl=false",
                                                "safe-password")
                                        .run(mock(ApplicationArguments.class)))
                .doesNotThrowAnyException();
        assertThatCode(
                        () ->
                                diagnostics(
                                                properties("key", "secret"),
                                                "r2dbc:postgresql://db/app",
                                                "safe-password")
                                        .run(mock(ApplicationArguments.class)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingAndPlaceholderCredentials() {
        assertThatThrownBy(
                        () ->
                                diagnostics(properties("", "secret"), "r2dbc:test", "safe-password")
                                        .run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KIWOOM_APP_KEY");
        assertThatThrownBy(
                        () ->
                                diagnostics(
                                                properties("key", "replace-secret"),
                                                "r2dbc:test",
                                                "safe-password")
                                        .run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KIWOOM_SECRET_KEY");
    }

    @Test
    void rejectsDefaultLoginPassword() {
        assertThatThrownBy(
                        () ->
                                diagnostics(properties("key", "secret"), "r2dbc:test", "change-me")
                                        .run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("기본값");
    }

    private LocalConfigurationDiagnostics diagnostics(
            KiwoomApiProperties properties, String databaseUrl, String password) {
        return new LocalConfigurationDiagnostics(properties, databaseUrl, "admin", password);
    }

    private KiwoomApiProperties properties(String key, String secret) {
        return new KiwoomApiProperties(
                "https://api.example.test",
                key,
                secret,
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                5,
                2,
                Duration.ofMillis(10),
                Duration.ofSeconds(3),
                Duration.ofMinutes(10));
    }
}
