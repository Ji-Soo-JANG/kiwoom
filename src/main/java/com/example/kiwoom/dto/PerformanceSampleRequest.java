package com.example.kiwoom.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record PerformanceSampleRequest(
        Long orderId,
        @Pattern(regexp = "\\d{6}") String code,
        @NotNull @Positive BigDecimal expectedPrice,
        @NotNull @Positive BigDecimal actualPrice,
        @NotNull BigDecimal netReturnRate) {}
