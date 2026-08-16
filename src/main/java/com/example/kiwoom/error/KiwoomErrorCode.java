package com.example.kiwoom.error;

import org.springframework.http.HttpStatus;

public enum KiwoomErrorCode {
    AUTHENTICATION_FAILED("KIWOOM_AUTHENTICATION_FAILED", HttpStatus.BAD_GATEWAY),
    RATE_LIMITED("KIWOOM_RATE_LIMITED", HttpStatus.SERVICE_UNAVAILABLE),
    STOCK_NOT_FOUND("KIWOOM_STOCK_NOT_FOUND", HttpStatus.NOT_FOUND),
    MARKET_CLOSED("KIWOOM_MARKET_CLOSED", HttpStatus.SERVICE_UNAVAILABLE),
    UPSTREAM_UNAVAILABLE("KIWOOM_UPSTREAM_UNAVAILABLE", HttpStatus.BAD_GATEWAY),
    INVALID_RESPONSE("KIWOOM_INVALID_RESPONSE", HttpStatus.BAD_GATEWAY);

    private final String apiCode;
    private final HttpStatus status;

    KiwoomErrorCode(String apiCode, HttpStatus status) {
        this.apiCode = apiCode;
        this.status = status;
    }

    public String apiCode() {
        return apiCode;
    }

    public HttpStatus status() {
        return status;
    }
}
