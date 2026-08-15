package com.example.kiwoom.service;

import com.example.kiwoom.config.KiwoomApiProperties;
import com.example.kiwoom.dto.DailyPriceResponse;
import com.example.kiwoom.dto.StockPriceResponse;
import com.example.kiwoom.error.KiwoomAuthenticationException;
import com.example.kiwoom.error.RetryableKiwoomException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class KiwoomApiService {

    private static final int MAX_STOCK_CODES = 20;
    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    private static final Duration TOKEN_REFRESH_MARGIN = Duration.ofMinutes(1);
    private static final DateTimeFormatter TOKEN_EXPIRY_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final Logger logger =
            Logger.getLogger(KiwoomApiService.class.getName());

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    private final String baseUrl;
    private final String apiKey;
    private final String apiSecret;
    private final Retry transientRetry;
    private final Duration currentPriceCacheTtl;
    private final Map<String, Mono<StockPriceResponse>> currentPriceCache =
            new ConcurrentHashMap<>();

    /*
     * 최초 요청 시 접근 토큰을 발급하고,
     * 이후 요청에서는 같은 토큰을 재사용합니다.
     */
    private final AtomicReference<AccessToken> cachedAccessToken =
            new AtomicReference<>();
    private Mono<AccessToken> tokenRefreshMono;

    public KiwoomApiService(
            WebClient webClient,
            KiwoomApiProperties properties,
            ObjectMapper objectMapper
    ) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.baseUrl = removeTrailingSlash(properties.baseUrl());
        this.apiKey = properties.key();
        this.apiSecret = properties.secret();
        this.currentPriceCacheTtl = properties.currentPriceCacheTtl();
        this.transientRetry = Retry.backoff(
                        properties.maxRetries(),
                        properties.retryBackoff()
                )
                .maxBackoff(properties.retryBackoff().multipliedBy(8))
                .filter(RetryableKiwoomException.class::isInstance)
                .onRetryExhaustedThrow(
                        (spec, signal) -> signal.failure()
                );

    }

    /**
     * 키움 접근 토큰을 발급합니다.
     */
    private Mono<AccessToken> issueAccessToken() {
        Map<String, String> requestBody = Map.of(
                "grant_type", "client_credentials",
                "appkey", apiKey,
                "secretkey", apiSecret
        );

        logger.info("키움 접근 토큰 발급 요청");

        return webClient.post()
                .uri(baseUrl + "/oauth2/token")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(
                        status -> status.value() == 429
                                || status.is5xxServerError(),
                        response -> Mono.just(
                                new RetryableKiwoomException(
                                        response.statusCode()
                                )
                        )
                )
                .onStatus(
                        status -> status.isError(),
                        response -> response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> {
                                    logger.warning(
                                            "토큰 발급 HTTP 오류: "
                                                    + response.statusCode()
                                                    + " / "
                                                    + body
                                    );

                                    return Mono.error(new RuntimeException(
                                            "접근 토큰 발급 실패 ("
                                                    + response.statusCode()
                                                    + "): "
                                                    + body
                                    ));
                                })
                )
                .bodyToMono(String.class)
                .flatMap(this::parseAccessTokenResponse)
                .retryWhen(transientRetry)
                .timeout(Duration.ofSeconds(10))
                .doOnSuccess(token ->
                        logger.info("키움 접근 토큰 발급 성공")
                )
                .doOnError(error ->
                        logger.warning(
                                "키움 접근 토큰 발급 실패: "
                                        + error.getMessage()
                        )
                );
    }

    /**
     * 접근 토큰 발급 응답을 파싱합니다.
     */
    private Mono<AccessToken> parseAccessTokenResponse(String jsonBody) {
        try {
            JsonNode root = objectMapper.readTree(jsonBody);

            int returnCode = root.path("return_code").asInt(-1);
            String returnMessage = root.path("return_msg").asText();

            if (returnCode != 0) {
                return Mono.error(new RuntimeException(
                        "키움 토큰 발급 오류 ["
                                + returnCode
                                + "]: "
                                + returnMessage
                ));
            }

            String token = root.path("token").asText();
            String expiresAtText = root.path("expires_dt").asText();

            if (token == null || token.isBlank()) {
                return Mono.error(new RuntimeException(
                        "토큰 발급 응답에 token이 없습니다: "
                                + jsonBody
                ));
            }

            if (expiresAtText == null || expiresAtText.isBlank()) {
                return Mono.error(new RuntimeException(
                        "토큰 발급 응답에 expires_dt가 없습니다"
                ));
            }

            Instant expiresAt = LocalDateTime.parse(
                    expiresAtText,
                    TOKEN_EXPIRY_FORMAT
            ).atZone(SEOUL_ZONE).toInstant();

            return Mono.just(new AccessToken(token, expiresAt));

        } catch (JsonProcessingException | DateTimeParseException e) {
            return Mono.error(new RuntimeException(
                    "토큰 응답 JSON 파싱 실패: "
                            + e.getMessage(),
                    e
            ));
        }
    }

    /**
     * 특정 종목의 현재가를 조회합니다.
     *
     * @param code 종목 코드, 예: 005930
     */
    public Mono<StockPriceResponse> getStockCurrentPrice(String code) {
        final String normalizedCode;
        try {
            normalizedCode = normalizeStockCode(code);
        } catch (IllegalArgumentException error) {
            return Mono.error(error);
        }

        if (currentPriceCacheTtl.isZero() || currentPriceCacheTtl.isNegative()) {
            return fetchStockCurrentPrice(normalizedCode);
        }

        return currentPriceCache.computeIfAbsent(normalizedCode, cacheKey ->
                fetchStockCurrentPrice(cacheKey)
                        .doOnError(error -> currentPriceCache.remove(cacheKey))
                        .cache(currentPriceCacheTtl)
        );
    }

    private Mono<StockPriceResponse> fetchStockCurrentPrice(String normalizedCode) {
        logger.info("주가 조회 요청: " + normalizedCode);

        return getAccessToken()
                .flatMap(token ->
                        requestStockCurrentPrice(
                                normalizedCode,
                                token.value()
                        )
                )
                .onErrorResume(
                        KiwoomAuthenticationException.class,
                        error -> retryStockCurrentPrice(normalizedCode)
                )
                .timeout(Duration.ofSeconds(10))
                .doOnError(error ->
                        logger.warning(
                                "주가 조회 실패 ["
                                        + normalizedCode
                                        + "]: "
                                        + error.getMessage()
                        )
                );
    }

    /**
     * 발급된 접근 토큰으로 ka10001을 호출합니다.
     */
    private Mono<StockPriceResponse> requestStockCurrentPrice(
            String code,
            String accessToken
    ) {
        Map<String, String> requestBody = Map.of(
                "stk_cd", code
        );

        return webClient.post()
                .uri(baseUrl + "/api/dostk/stkinfo")
                .header(
                        "Authorization",
                        "Bearer " + accessToken
                )
                .header("api-id", "ka10001")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(
                        status -> status.value() == 401,
                        response -> response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(body -> new KiwoomAuthenticationException(
                                        "키움 인증이 만료되었습니다"
                                ))
                )
                .onStatus(
                        status -> status.value() == 429
                                || status.is5xxServerError(),
                        response -> Mono.just(
                                new RetryableKiwoomException(
                                        response.statusCode()
                                )
                        )
                )
                .onStatus(
                        status -> status.isError(),
                        response -> response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> {
                                    logger.warning(
                                            "주가 조회 HTTP 오류: "
                                                    + response.statusCode()
                                                    + " / "
                                                    + body
                                    );

                                    return Mono.error(new RuntimeException(
                                            "주가 조회 API 호출 실패 ("
                                                    + response.statusCode()
                                                    + "): "
                                                    + body
                                    ));
                                })
                )
                .bodyToMono(String.class)
                .flatMap(body ->
                        parseStockPriceResponse(code, body)
                )
                .retryWhen(transientRetry);
    }

    /**
     * ka10001 응답을 StockPriceResponse로 변환합니다.
     */
    private Mono<StockPriceResponse> parseStockPriceResponse(
            String requestedCode,
            String jsonBody
    ) {
        try {
            JsonNode root = objectMapper.readTree(jsonBody);

            int returnCode = root.path("return_code").asInt(0);
            String returnMessage =
                    root.path("return_msg").asText();

            if (returnCode != 0) {
                return Mono.error(new RuntimeException(
                        "키움 주가 조회 오류 ["
                                + returnCode
                                + "]: "
                                + returnMessage
                ));
            }

            /*
             * ka10001 주요 응답 필드
             *
             * stk_cd   : 종목 코드
             * cur_prc  : 현재가
             * pred_pre : 전일 대비
             * flu_rt   : 등락률
             */
            String responseCode =
                    root.path("stk_cd").asText();

            if (responseCode == null
                    || responseCode.isBlank()) {
                responseCode = requestedCode;
            }

            String currentPrice = normalizePrice(
                    root.path("cur_prc").asText("0")
            );

            String changeAmount = normalizeNumber(
                    root.path("pred_pre").asText("0")
            );

            String changeRate = normalizeNumber(
                    root.path("flu_rt").asText("0.00")
            );

            StockPriceResponse response =
                    new StockPriceResponse(
                            responseCode,
                            currentPrice,
                            changeAmount,
                            changeRate
                    );

            logger.info(
                    "주가 조회 성공: "
                            + responseCode
                            + " / "
                            + currentPrice
            );

            return Mono.just(response);

        } catch (JsonProcessingException e) {
            return Mono.error(new RuntimeException(
                    "주가 응답 JSON 파싱 실패: "
                            + e.getMessage(),
                    e
            ));
        }
    }

    /**
     * 여러 종목의 현재가를 조회합니다.
     * 한 종목이라도 조회에 실패하면 일부 목록을 반환하지 않고 전체 요청을 실패시킵니다.
     */
    public Mono<List<StockPriceResponse>> getMultipleStockPrices(
            List<String> codes
    ) {
        if (codes == null || codes.isEmpty()) {
            return Mono.error(
                    new IllegalArgumentException(
                            "종목 코드 목록은 필수입니다"
                    )
            );
        }

        final List<String> normalizedCodes;
        try {
            normalizedCodes = List.copyOf(new LinkedHashSet<>(
                    codes.stream()
                            .map(this::normalizeStockCode)
                            .toList()
            ));
        } catch (IllegalArgumentException error) {
            return Mono.error(error);
        }

        if (normalizedCodes.size() > MAX_STOCK_CODES) {
            return Mono.error(new IllegalArgumentException(
                    "한 번에 조회할 수 있는 종목은 최대 "
                            + MAX_STOCK_CODES
                            + "개입니다"
            ));
        }

        return Flux.fromIterable(normalizedCodes)
                /*
                 * 키움 API에 동시에 너무 많은 요청을 보내지 않도록
                 * 최대 동시 요청 수를 3으로 제한합니다.
                 */
                .flatMap(this::getStockCurrentPrice, 3)
                .collectList();
    }

    /**
     * 현재가는 절댓값으로 정규화합니다.
     *
     * 예: -75000 → 75000
     */
    private String normalizePrice(String value) {
        if (value == null || value.isBlank()) {
            return "0";
        }

        return value
                .trim()
                .replace(",", "")
                .replace("+", "")
                .replace("-", "");
    }

    /**
     * 전일 대비와 등락률은 부호를 유지합니다.
     */
    private String normalizeNumber(String value) {
        if (value == null || value.isBlank()) {
            return "0";
        }

        return value
                .trim()
                .replace(",", "");
    }

    private String abbreviate(
            String value,
            int maxLength
    ) {
        if (value == null) {
            return "";
        }

        if (value.length() <= maxLength) {
            return value;
        }

        return value.substring(0, maxLength)
                + "...";
    }

    private static String removeTrailingSlash(
            String value
    ) {
        if (value == null) {
            return "";
        }

        while (value.endsWith("/")) {
            value = value.substring(
                    0,
                    value.length() - 1
            );
        }

        return value;
    }

    public Mono<List<DailyPriceResponse>> getDailyPrices(
            String code,
            String baseDate
    ) {
        final String normalizedCode;
        try {
            normalizedCode = normalizeStockCode(code);
        } catch (IllegalArgumentException error) {
            return Mono.error(error);
        }

        String normalizedBaseDate =
                baseDate == null || baseDate.isBlank()
                        ? java.time.LocalDate.now(
                        java.time.ZoneId.of("Asia/Seoul")
                ).format(
                        java.time.format.DateTimeFormatter.BASIC_ISO_DATE
                )
                        : baseDate.trim();

        try {
            LocalDate.parse(
                    normalizedBaseDate,
                    DateTimeFormatter.BASIC_ISO_DATE
            );
        } catch (DateTimeParseException error) {
            return Mono.error(
                    new IllegalArgumentException(
                            "기준일자는 유효한 yyyyMMdd 날짜여야 합니다"
                    )
            );
        }

        return getAccessToken().flatMap(token ->
                requestDailyPrices(
                        normalizedCode,
                        normalizedBaseDate,
                        token.value()
                )
        ).onErrorResume(
                KiwoomAuthenticationException.class,
                error -> retryDailyPrices(normalizedCode, normalizedBaseDate)
        );
    }

    private Mono<List<DailyPriceResponse>> requestDailyPrices(
            String code,
            String baseDate,
            String accessToken
    ) {
        Map<String, String> requestBody = Map.of(
                "stk_cd", code,
                "base_dt", baseDate,
                "upd_stkpc_tp", "1"
        );

        logger.info(
                "일봉 조회 요청: "
                        + code
                        + " / "
                        + baseDate
        );

        return webClient.post()
                .uri(baseUrl + "/api/dostk/chart")
                .header(
                        "Authorization",
                        "Bearer " + accessToken
                )
                .header("api-id", "ka10081")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(
                        status -> status.value() == 401,
                        response -> response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(body -> new KiwoomAuthenticationException(
                                        "키움 인증이 만료되었습니다"
                                ))
                )
                .onStatus(
                        status -> status.value() == 429
                                || status.is5xxServerError(),
                        response -> Mono.just(
                                new RetryableKiwoomException(
                                        response.statusCode()
                                )
                        )
                )
                .onStatus(
                        status -> status.isError(),
                        response -> response
                                .bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> Mono.error(
                                        new RuntimeException(
                                                "일봉 API 호출 실패 ("
                                                        + response.statusCode()
                                                        + "): "
                                                        + body
                                        )
                                ))
                )
                .bodyToMono(String.class)
                .flatMap(this::parseDailyPrices)
                .retryWhen(transientRetry)
                .timeout(Duration.ofSeconds(15));
    }

    private Mono<List<DailyPriceResponse>> parseDailyPrices(
            String jsonBody
    ) {
        try {
            JsonNode root = objectMapper.readTree(jsonBody);

            int returnCode =
                    root.path("return_code").asInt(0);

            if (returnCode != 0) {
                return Mono.error(new RuntimeException(
                        "키움 일봉 조회 오류 ["
                                + returnCode
                                + "]: "
                                + root.path("return_msg").asText()
                ));
            }

            JsonNode dailyArray =
                    root.path("stk_dt_pole_chart_qry");

            if (!dailyArray.isArray()) {
                return Mono.error(new RuntimeException(
                        "일봉 배열을 찾을 수 없습니다: "
                                + abbreviate(jsonBody, 500)
                ));
            }

            List<DailyPriceResponse> result =
                    new java.util.ArrayList<>();

            for (JsonNode item : dailyArray) {
                result.add(new DailyPriceResponse(
                        item.path("dt").asText(),
                        parseLong(item.path("open_pric").asText()),
                        parseLong(item.path("high_pric").asText()),
                        parseLong(item.path("low_pric").asText()),
                        parseLong(item.path("cur_prc").asText()),
                        parseLong(item.path("trde_qty").asText())
                ));
            }

            // 키움 응답은 일반적으로 최신 날짜부터 나오므로
            // 차트 표시를 위해 과거 날짜부터 정렬합니다.
            result.sort(
                    java.util.Comparator.comparing(
                            DailyPriceResponse::getDate
                    )
            );

            return Mono.just(result);

        } catch (JsonProcessingException e) {
            return Mono.error(new RuntimeException(
                    "일봉 응답 JSON 파싱 실패",
                    e
            ));
        }
    }

    private long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }

        try {
            return Math.abs(
                    Long.parseLong(
                            value
                                    .trim()
                                    .replace(",", "")
                                    .replace("+", "")
                    )
            );
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private String normalizeStockCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("종목 코드는 필수입니다");
        }

        String normalizedCode = code.trim();
        if (!normalizedCode.matches("\\d{6}")) {
            throw new IllegalArgumentException(
                    "종목 코드는 6자리 숫자여야 합니다"
            );
        }

        return normalizedCode;
    }

    private Mono<AccessToken> getAccessToken() {
        return Mono.defer(() -> {
            AccessToken token = cachedAccessToken.get();
            if (token != null && token.isUsable()) {
                return Mono.just(token);
            }
            return refreshAccessToken();
        });
    }

    private synchronized Mono<AccessToken> refreshAccessToken() {
        AccessToken token = cachedAccessToken.get();
        if (token != null && token.isUsable()) {
            return Mono.just(token);
        }
        if (tokenRefreshMono == null) {
            tokenRefreshMono = issueAccessToken()
                    .doOnNext(cachedAccessToken::set)
                    .doFinally(signal -> clearTokenRefresh())
                    .cache();
        }
        return tokenRefreshMono;
    }

    private synchronized void clearTokenRefresh() {
        tokenRefreshMono = null;
    }

    private void invalidateAccessToken() {
        cachedAccessToken.set(null);
    }

    private Mono<StockPriceResponse> retryStockCurrentPrice(String code) {
        invalidateAccessToken();
        return getAccessToken().flatMap(token ->
                requestStockCurrentPrice(code, token.value())
        );
    }

    private Mono<List<DailyPriceResponse>> retryDailyPrices(
            String code,
            String baseDate
    ) {
        invalidateAccessToken();
        return getAccessToken().flatMap(token ->
                requestDailyPrices(code, baseDate, token.value())
        );
    }

    private record AccessToken(String value, Instant expiresAt) {

        private boolean isUsable() {
            return expiresAt.isAfter(Instant.now().plus(TOKEN_REFRESH_MARGIN));
        }
    }
}
