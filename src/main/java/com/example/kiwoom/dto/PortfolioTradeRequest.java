package com.example.kiwoom.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public record PortfolioTradeRequest(
        @Pattern(regexp = "\\d{6}") String code,
        @NotNull TradeType type,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 16, fraction = 4)
                BigDecimal quantity,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 16, fraction = 4)
                BigDecimal price,
        @DecimalMin("0") @Digits(integer = 16, fraction = 4) BigDecimal fee,
        @DecimalMin("0") @Digits(integer = 16, fraction = 4) BigDecimal tax) {}
