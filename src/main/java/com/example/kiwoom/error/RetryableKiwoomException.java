package com.example.kiwoom.error;

import org.springframework.http.HttpStatusCode;

public class RetryableKiwoomException extends KiwoomApiException {

    public RetryableKiwoomException(HttpStatusCode statusCode) {
        super(
                statusCode.value() == 429
                        ? KiwoomErrorCode.RATE_LIMITED
                        : KiwoomErrorCode.UPSTREAM_UNAVAILABLE,
                "일시적인 키움 API 오류: " + statusCode,
                statusCode.value());
    }

    public RetryableKiwoomException(String message, Throwable cause) {
        super(KiwoomErrorCode.UPSTREAM_UNAVAILABLE, message);
        initCause(cause);
    }
}
