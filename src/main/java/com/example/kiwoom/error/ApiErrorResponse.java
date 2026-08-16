package com.example.kiwoom.error;

import java.time.Instant;

public record ApiErrorResponse(
        String code,
        String message,
        Instant timestamp,
        String path,
        String traceId
) {
}
