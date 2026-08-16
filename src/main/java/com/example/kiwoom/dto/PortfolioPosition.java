package com.example.kiwoom.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public record PortfolioPosition(
        @Pattern(regexp = "\\d{6}", message = "종목 코드는 6자리 숫자여야 합니다") String code,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 16, fraction = 4)
                BigDecimal quantity,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 16, fraction = 4)
                BigDecimal averagePrice) {}
