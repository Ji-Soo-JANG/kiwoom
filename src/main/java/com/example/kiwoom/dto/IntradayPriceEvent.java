package com.example.kiwoom.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;

public record IntradayPriceEvent(
        Long id,
        @NotBlank String sourceEventId,
        @NotBlank @Pattern(regexp = "\\d{6}") String code,
        @NotNull Instant eventTime,
        @Positive long price,
        @PositiveOrZero long volume,
        Instant receivedAt) {}
