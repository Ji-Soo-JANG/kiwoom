package com.example.kiwoom.dto;

import jakarta.validation.constraints.NotBlank;

public record KillSwitchRequest(@NotBlank String reason) {}
