package com.example.kiwoom.error;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleBadRequest(
            IllegalArgumentException error,
            ServerWebExchange exchange
    ) {
        return response("INVALID_REQUEST", error, exchange);
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ApiErrorResponse handleUpstreamError(
            RuntimeException error,
            ServerWebExchange exchange
    ) {
        return response("KIWOOM_API_ERROR", error, exchange);
    }

    private ApiErrorResponse response(
            String code,
            RuntimeException error,
            ServerWebExchange exchange
    ) {
        return new ApiErrorResponse(
                code,
                error.getMessage(),
                Instant.now(),
                exchange.getRequest().getPath().value()
        );
    }
}
