package com.example.kiwoom.dto;

import java.math.BigDecimal;

public record AlertRuleRequest(
        String code, AlertConditionType conditionType, BigDecimal threshold) {}
