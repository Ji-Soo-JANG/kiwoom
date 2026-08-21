package com.example.kiwoom.dto;

import java.math.BigDecimal;

public record PaperPosition(String code, long quantity, BigDecimal averagePrice) {}
