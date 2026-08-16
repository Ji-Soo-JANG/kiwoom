package com.example.kiwoom.dto;

public record MarketRankingItem(
        String code, String name, long currentPrice, double changeRate, long volume) {}
