package com.example.kiwoom.research.walkforward.dto;

import com.example.kiwoom.research.backtest.dto.BacktestRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record WalkForwardRequest(
        @Valid @NotNull BacktestRequest backtest,
        @Min(60) Integer trainingDays,
        @Min(20) Integer validationDays,
        @Min(20) Integer stepDays) {}
