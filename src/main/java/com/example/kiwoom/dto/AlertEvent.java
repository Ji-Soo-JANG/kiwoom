package com.example.kiwoom.dto;
import java.math.BigDecimal; import java.time.OffsetDateTime;
public record AlertEvent(Long id,Long ruleId,String code,AlertConditionType conditionType,BigDecimal observedValue,BigDecimal threshold,OffsetDateTime triggeredAt,OffsetDateTime readAt) {}
