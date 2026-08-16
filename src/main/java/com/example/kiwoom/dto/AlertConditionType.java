package com.example.kiwoom.dto;
public enum AlertConditionType {
    PRICE_ABOVE, PRICE_BELOW, RSI_ABOVE, RSI_BELOW, MACD_CROSS_UP, MACD_CROSS_DOWN;

    public boolean requiresThreshold() {
        return this != MACD_CROSS_UP && this != MACD_CROSS_DOWN;
    }
}
