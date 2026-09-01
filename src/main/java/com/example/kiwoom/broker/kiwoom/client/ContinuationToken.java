package com.example.kiwoom.broker.kiwoom.client;

public record ContinuationToken(String value) {
    public ContinuationToken {
        if (value != null && value.isBlank()) value = null;
    }

    public boolean present() {
        return value != null && !value.isBlank();
    }
}
