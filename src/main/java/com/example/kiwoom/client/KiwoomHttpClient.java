package com.example.kiwoom.client;

import com.example.kiwoom.config.KiwoomApiProperties;
import com.example.kiwoom.error.KiwoomAuthenticationException;
import com.example.kiwoom.error.KiwoomApiException;
import com.example.kiwoom.error.RetryableKiwoomException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.util.Map;

@Component
public class KiwoomHttpClient {
    private final WebClient webClient;
    private final String baseUrl;
    private final Retry transientRetry;

    public KiwoomHttpClient(WebClient webClient, KiwoomApiProperties properties) {
        this.webClient = webClient;
        this.baseUrl = removeTrailingSlash(properties.baseUrl());
        this.transientRetry = Retry.backoff(properties.maxRetries(), properties.retryBackoff())
                .maxBackoff(properties.retryBackoff().multipliedBy(8))
                .filter(RetryableKiwoomException.class::isInstance)
                .onRetryExhaustedThrow((spec, signal) -> signal.failure());
    }

    public Mono<String> issueAccessToken(String apiKey, String apiSecret) {
        return post("/oauth2/token", null, null, Map.of(
                "grant_type", "client_credentials", "appkey", apiKey, "secretkey", apiSecret
        ), "접근 토큰 발급 실패");
    }

    public Mono<String> requestCurrentPrice(String code, String accessToken) {
        return post("/api/dostk/stkinfo", accessToken, "ka10001", Map.of("stk_cd", code),
                "주가 조회 API 호출 실패");
    }

    public Mono<String> requestDailyPrices(String code, String baseDate, String accessToken) {
        return post("/api/dostk/chart", accessToken, "ka10081", Map.of(
                "stk_cd", code, "base_dt", baseDate, "upd_stkpc_tp", "1"
        ), "일봉 API 호출 실패");
    }

    private Mono<String> post(String path, String token, String apiId,
                              Map<String, String> body, String failureMessage) {
        WebClient.RequestBodySpec request = webClient.post().uri(baseUrl + path);
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (apiId != null) request.header("api-id", apiId);

        return request.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                .bodyValue(body).retrieve()
                .onStatus(status -> status.value() == 401,
                        response -> Mono.just(new KiwoomAuthenticationException("키움 인증이 만료되었습니다")))
                .onStatus(status -> status.value() == 429 || status.is5xxServerError(),
                        response -> Mono.just(new RetryableKiwoomException(response.statusCode())))
                .onStatus(status -> status.isError(), response -> response.bodyToMono(String.class)
                        .defaultIfEmpty(failureMessage)
                        .map(bodyText -> KiwoomApiException.fromResponse(response.statusCode().value(),
                                failureMessage + " (" + response.statusCode() + "): " + bodyText)))
                .bodyToMono(String.class)
                .retryWhen(transientRetry);
    }

    private static String removeTrailingSlash(String value) {
        if (value == null) return "";
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }
}
