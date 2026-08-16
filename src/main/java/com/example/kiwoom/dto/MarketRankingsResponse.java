package com.example.kiwoom.dto;

import java.time.Instant;
import java.util.List;

public record MarketRankingsResponse(
        List<MarketRankingItem> gainers,
        List<MarketRankingItem> losers,
        List<MarketRankingItem> mostTraded,
        Instant updatedAt) {}
