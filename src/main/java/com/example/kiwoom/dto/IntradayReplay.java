package com.example.kiwoom.dto;

import java.time.Instant;
import java.util.List;

public record IntradayReplay(
        String code,
        int eventCount,
        Instant firstEventTime,
        Instant lastEventTime,
        String checksum,
        List<IntradayPriceEvent> events) {}
