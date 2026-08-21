package com.example.kiwoom.config;

import com.example.kiwoom.dto.TradingMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.trading")
public record TradingProperties(
        @NotNull TradingMode mode,
        boolean liveEnabled,
        String liveConfirmation,
        @Positive BigDecimal paperInitialCash,
        @Positive BigDecimal maxPositionRate,
        @Positive BigDecimal maxGrossExposureRate,
        @Positive BigDecimal maxDailyLossRate,
        @Positive BigDecimal maxDrawdownRate,
        @Positive int maxOpenPositions) {}
