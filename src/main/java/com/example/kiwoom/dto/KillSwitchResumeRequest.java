package com.example.kiwoom.dto;

import jakarta.validation.constraints.NotBlank;

public record KillSwitchResumeRequest(@NotBlank String confirmation, @NotBlank String reason) {}
