package com.example.kiwoom.research.backtest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;

public record BacktestRequest(
        @NotBlank @Pattern(regexp = "\\d{6}") String code,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @Positive Double initialCapital,
        @Positive Double positionSizeRate,
        @PositiveOrZero Double feeRate,
        @PositiveOrZero Double taxRate,
        @PositiveOrZero Double slippageRate,
        @Positive Double stopLossRate,
        @Positive Double takeProfitRate,
        @Positive Integer maxHoldingDays,
        @Positive Integer boxRangeDays) {}
