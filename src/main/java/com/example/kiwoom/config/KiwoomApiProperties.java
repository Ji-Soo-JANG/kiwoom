package com.example.kiwoom.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "kiwoom.api")
public record KiwoomApiProperties(
        @NotBlank String baseUrl,
        @NotBlank String key,
        @NotBlank String secret,
        @NotNull Duration connectTimeout,
        @NotNull Duration responseTimeout,
        @Positive int maxConnections,
        @Positive int maxRetries,
        @NotNull Duration retryBackoff,
        @NotNull Duration currentPriceCacheTtl,
        @NotNull Duration dailyPriceCacheTtl) {}
