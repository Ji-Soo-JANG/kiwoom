package com.example.kiwoom.error;

import com.example.kiwoom.config.RequestTraceFilter;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleBadRequest(
            IllegalArgumentException error, ServerWebExchange exchange) {
        return response("INVALID_REQUEST", error, exchange);
    }

    @ExceptionHandler(WebExchangeBindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleValidationError(
            WebExchangeBindException error, ServerWebExchange exchange) {
        return response("INVALID_REQUEST", error, exchange);
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ApiErrorResponse handleUpstreamError(
            RuntimeException error, ServerWebExchange exchange) {
        return response("KIWOOM_API_ERROR", error, exchange);
    }

    @ExceptionHandler(KiwoomApiException.class)
    public ResponseEntity<ApiErrorResponse> handleKiwoomError(
            KiwoomApiException error, ServerWebExchange exchange) {
        return ResponseEntity.status(error.errorCode().status())
                .body(response(error.errorCode().apiCode(), error, exchange));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            ResourceNotFoundException error, ServerWebExchange exchange) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(response("RESOURCE_NOT_FOUND", error, exchange));
    }

    private ApiErrorResponse response(
            String code, RuntimeException error, ServerWebExchange exchange) {
        return new ApiErrorResponse(
                code,
                error.getMessage(),
                Instant.now(),
                exchange.getRequest().getPath().value(),
                exchange.getAttribute(RequestTraceFilter.TRACE_ID_ATTRIBUTE));
    }
}
