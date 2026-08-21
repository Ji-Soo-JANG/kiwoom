package com.example.kiwoom.dto;

import jakarta.validation.constraints.NotBlank;

public record ObservationRequest(@NotBlank String name, @NotBlank String strategyVersion) {}
