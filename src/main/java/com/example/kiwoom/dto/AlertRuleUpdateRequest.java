package com.example.kiwoom.dto;

import java.math.BigDecimal;

public record AlertRuleUpdateRequest(BigDecimal threshold, Boolean enabled) {
}
