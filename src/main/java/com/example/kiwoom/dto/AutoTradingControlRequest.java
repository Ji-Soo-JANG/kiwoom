package com.example.kiwoom.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AutoTradingControlRequest(
        @NotNull Boolean paperEnabled,
        @NotBlank String paperStrategy,
        @NotNull Boolean liveEnabled,
        @NotBlank String liveStrategy,
        String liveConfirmation) {}
