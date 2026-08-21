package com.example.kiwoom.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

public record ObservationSampleRequest(
        @NotNull LocalDate tradingDay,
        @Pattern(regexp = "\\d{6}") String code,
        boolean backtestSignal,
        boolean realtimeSignal,
        @Positive Long expectedPrice,
        @Positive Long observedPrice) {}
