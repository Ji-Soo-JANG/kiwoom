package com.example.kiwoom.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record PaperOrderRequest(
        @NotBlank String decisionId,
        @NotBlank @Pattern(regexp = "\\d{6}") String code,
        @NotNull OrderSide side,
        @Positive long quantity,
        @NotNull @Positive BigDecimal price) {}
