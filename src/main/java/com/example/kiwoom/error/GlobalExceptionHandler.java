package com.example.kiwoom.error;

import com.example.kiwoom.config.RequestTraceFilter;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiErrorResponse> handleDatabaseError(
            DataAccessException error, ServerWebExchange exchange) {
        log.warn(
                "database_error path={} errorType={}",
                exchange.getRequest().getPath().value(),
                error.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response("DATABASE_ERROR", error, exchange));
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
