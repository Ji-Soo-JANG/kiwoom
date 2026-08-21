package com.example.kiwoom.dto;

import jakarta.validation.constraints.NotBlank;

public record PaperExitApprovalRequest(@NotBlank String confirmation) {}
