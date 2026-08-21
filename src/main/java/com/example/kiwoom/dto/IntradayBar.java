package com.example.kiwoom.dto;

import java.time.Instant;

public record IntradayBar(
        Instant minute,
        long openPrice,
        long highPrice,
        long lowPrice,
        long closePrice,
        long volume,
        int eventCount) {}
