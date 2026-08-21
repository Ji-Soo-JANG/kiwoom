package com.example.kiwoom.dto;

import jakarta.validation.constraints.NotBlank;

public record TradeApprovalRequest(@NotBlank String confirmation) {}
