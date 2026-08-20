package com.example.kiwoom.client;

import com.example.kiwoom.config.KiwoomApiProperties;
import com.example.kiwoom.error.KiwoomApiException;
import com.example.kiwoom.error.KiwoomAuthenticationException;
import com.example.kiwoom.error.KiwoomErrorCode;
import com.example.kiwoom.error.RetryableKiwoomException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Component
public class KiwoomHttpClient {
    private final WebClient webClient;
    private final String baseUrl;
    private final Retry transientRetry;
    private final MeterRegistry meterRegistry;

    public KiwoomHttpClient(
            WebClient webClient, KiwoomApiProperties properties, MeterRegistry meterRegistry) {
        this.webClient = webClient;
        this.meterRegistry = meterRegistry;
        this.baseUrl = removeTrailingSlash(properties.baseUrl());
        this.transientRetry =
                Retry.backoff(properties.maxRetries(), properties.retryBackoff())
                        .maxBackoff(properties.retryBackoff().multipliedBy(8))
                        .filter(RetryableKiwoomException.class::isInstance)
                        .doBeforeRetry(
                                signal ->
                                        Counter.builder("kiwoom.api.retries")
                                                .description("키움 API 재시도 횟수")
                                                .tag(
                                                        "reason",
                                                        signal.failure()
                                                                                instanceof
                                                                                KiwoomApiException
                                                                                                error
                                                                        && error.errorCode()
                                                                                == KiwoomErrorCode
                                                                                        .RATE_LIMITED
                                                                ? "rate_limited"
                                                                : "unavailable")
                                                .register(meterRegistry)
                                                .increment())
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure());
    }

    public Mono<String> issueAccessToken(String apiKey, String apiSecret) {
        return post(
                "/oauth2/token",
                null,
                null,
                Map.of(
                        "grant_type",
                        "client_credentials",
                        "appkey",
                        apiKey,
                        "secretkey",
                        apiSecret),
                "접근 토큰 발급 실패");
    }

    public Mono<String> requestCurrentPrice(String code, String accessToken) {
        return post(
                "/api/dostk/stkinfo",
                accessToken,
                "ka10001",
                Map.of("stk_cd", code),
                "주가 조회 API 호출 실패");
    }

    public Mono<String> requestDailyPrices(String code, String baseDate, String accessToken) {
        return requestPeriodPrices(code, baseDate, "ka10081", accessToken);
    }

    /** 기간별 차트 조회. apiId는 일봉 ka10081, 주봉 ka10082, 월봉 ka10083, 년봉 ka10094입니다. */
    public Mono<String> requestPeriodPrices(
            String code, String baseDate, String apiId, String accessToken) {
        return post(
                "/api/dostk/chart",
                accessToken,
                apiId,
                Map.of("stk_cd", code, "base_dt", baseDate, "upd_stkpc_tp", "1"),
                "차트 API 호출 실패");
    }

    public Mono<String> requestStockList(String marketType, String accessToken) {
        return post(
                "/api/dostk/stkinfo",
                accessToken,
                "ka10099",
                Map.of("mrkt_tp", marketType),
                "종목 목록 API 호출 실패");
    }

    public Mono<String> requestAccountNumber(String accessToken) {
        return post("/api/dostk/acnt", accessToken, "ka00001", Map.of(), "계좌번호 조회 API 호출 실패");
    }

    public Mono<String> requestAccountPortfolio(String accessToken) {
        return post(
                "/api/dostk/acnt",
                accessToken,
                "kt00018",
                Map.of("qry_tp", "1", "dmst_stex_tp", "KRX"),
                "계좌 평가잔고 API 호출 실패");
    }

    public Mono<String> requestChangeRateRanking(
            String marketType, String sortType, String accessToken) {
        return post(
                "/api/dostk/rkinfo",
                accessToken,
                "ka10027",
                Map.of(
                        "mrkt_tp", marketType,
                        "sort_tp", sortType,
                        "trde_qty_cnd", "0000",
                        "stk_cnd", "0",
                        "crd_cnd", "0",
                        "updown_incls", "1",
                        "pric_cnd", "0",
                        "trde_prica_cnd", "0",
                        "stex_tp", "3"),
                "등락률 순위 API 호출 실패");
    }

    public Mono<String> requestVolumeRanking(String marketType, String accessToken) {
        return post(
                "/api/dostk/rkinfo",
                accessToken,
                "ka10030",
                Map.of(
                        "mrkt_tp", marketType,
                        "sort_tp", "1",
                        "mang_stk_incls", "0",
                        "crd_tp", "0",
                        "trde_qty_tp", "0",
                        "pric_tp", "0",
                        "trde_prica_tp", "0",
                        "mrkt_open_tp", "0",
                        "stex_tp", "3"),
                "거래량 순위 API 호출 실패");
    }

    private Mono<String> post(
            String path,
            String token,
            String apiId,
            Map<String, String> body,
            String failureMessage) {
        return Mono.defer(
                        () -> {
                            Timer.Sample sample = Timer.start(meterRegistry);
                            WebClient.RequestBodySpec request =
                                    webClient.post().uri(baseUrl + path);
                            if (token != null) request.header("Authorization", "Bearer " + token);
                            if (apiId != null) request.header("api-id", apiId);
                            return request.contentType(MediaType.APPLICATION_JSON)
                                    .accept(MediaType.APPLICATION_JSON)
                                    .bodyValue(body)
                                    .retrieve()
                                    .onStatus(
                                            status -> status.value() == 401,
                                            response ->
                                                    Mono.just(
                                                            new KiwoomAuthenticationException(
                                                                    "키움 인증이 만료되었습니다")))
                                    .onStatus(
                                            status ->
                                                    status.value() == 429
                                                            || status.is5xxServerError(),
                                            response ->
                                                    Mono.just(
                                                            new RetryableKiwoomException(
                                                                    response.statusCode())))
                                    .onStatus(
                                            status -> status.isError(),
                                            response ->
                                                    response.bodyToMono(String.class)
                                                            .defaultIfEmpty(failureMessage)
                                                            .map(
                                                                    bodyText ->
                                                                            KiwoomApiException
                                                                                    .fromResponse(
                                                                                            response.statusCode()
                                                                                                    .value(),
                                                                                            failureMessage
                                                                                                    + " ("
                                                                                                    + response
                                                                                                            .statusCode()
                                                                                                    + "): "
                                                                                                    + bodyText)))
                                    .bodyToMono(String.class)
                                    .onErrorMap(
                                            WebClientRequestException.class,
                                            error ->
                                                    new RetryableKiwoomException(
                                                            "키움 API 네트워크 연결 또는 응답 시간 초과", error))
                                    .onErrorMap(
                                            KiwoomHttpClient::isTransientResponseFailure,
                                            error ->
                                                    new RetryableKiwoomException(
                                                            "키움 API 응답을 읽는 중 연결이 끊어졌습니다", error))
                                    .doOnSuccess(value -> record(sample, path, "success"))
                                    .doOnError(error -> record(sample, path, "failure"));
                        })
                .retryWhen(transientRetry);
    }

    private void record(Timer.Sample sample, String path, String outcome) {
        sample.stop(
                Timer.builder("kiwoom.api.request.duration")
                        .description("키움 API 응답 시간")
                        .tag("endpoint", path)
                        .tag("outcome", outcome)
                        .register(meterRegistry));
        Counter.builder("kiwoom.api.requests")
                .description("키움 API 요청 횟수")
                .tag("endpoint", path)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }

    /**
     * HTTP 200을 받았지만 본문을 읽는 도중 연결이 끊어지는 등 일시적으로 재시도 가능한 응답 수신 오류인지 판단합니다. 키움 서버가 본문을 모두 보내기 전에 연결을
     * 닫으면 WebClient는 원인을 숨기고 "200 OK from POST ..." 형태의 WebClientResponseException만 남기므로 상태 코드로
     * 판별합니다.
     */
    private static boolean isTransientResponseFailure(Throwable error) {
        if (error instanceof WebClientResponseException responseError) {
            return responseError.getStatusCode().is2xxSuccessful();
        }
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            String type = cause.getClass().getSimpleName();
            if (type.equals("PrematureCloseException") || type.equals("AbortedException")) {
                return true;
            }
        }
        return false;
    }

    private static String removeTrailingSlash(String value) {
        if (value == null) return "";
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }
}
