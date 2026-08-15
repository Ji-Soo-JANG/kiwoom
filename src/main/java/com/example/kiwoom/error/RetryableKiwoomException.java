package com.example.kiwoom.error;

import org.springframework.http.HttpStatusCode;

public class RetryableKiwoomException extends RuntimeException {

    public RetryableKiwoomException(HttpStatusCode statusCode) {
        super("일시적인 키움 API 오류: " + statusCode);
    }
}
