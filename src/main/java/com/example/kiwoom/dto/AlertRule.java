package com.example.kiwoom.dto;
import java.math.BigDecimal;
public record AlertRule(Long id,String code,AlertConditionType conditionType,BigDecimal threshold,boolean enabled,boolean lastState) {}
