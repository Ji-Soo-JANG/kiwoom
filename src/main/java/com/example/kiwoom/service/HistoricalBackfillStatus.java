package com.example.kiwoom.service;

public enum HistoricalBackfillStatus {
    PENDING,
    IN_PROGRESS,
    TARGET_REACHED,
    HISTORY_EXHAUSTED,
    ALREADY_SATISFIED,
    FAILED
}
