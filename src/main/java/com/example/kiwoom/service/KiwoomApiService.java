package com.example.kiwoom.service;

import com.example.kiwoom.client.KiwoomHttpClient;
import com.example.kiwoom.config.KiwoomApiProperties;
import com.example.kiwoom.dto.DailyPriceResponse;
import com.example.kiwoom.dto.StockPriceResponse;
import com.example.kiwoom.dto.StockSearchResult;
import com.example.kiwoom.error.KiwoomAuthenticationException;
import com.example.kiwoom.mapper.KiwoomResponseMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class KiwoomApiService {
    private static final int MAX_STOCK_CODES = 20;
    private static final Duration TOKEN_REFRESH_MARGIN = Duration.ofMinutes(1);
    private static final Logger logger = LoggerFactory.getLogger(KiwoomApiService.class);

    private final KiwoomHttpClient client;
    private final KiwoomResponseMapper mapper;
    private final String apiKey;
    private final String apiSecret;
    private final Duration currentPriceCacheTtl;
    private final Duration dailyPriceCacheTtl;
    private final Map<String, Mono<StockPriceResponse>> currentPriceCache =
            new ConcurrentHashMap<>();
    private final Map<DailyCacheKey, Mono<List<DailyPriceResponse>>> dailyPriceCache =
            new ConcurrentHashMap<>();
    private final Counter currentCacheHits;
    private final Counter currentCacheMisses;
    private final Counter dailyCacheHits;
    private final Counter dailyApiCalls;
    private final Counter tokenRefreshes;
    private final AtomicReference<AccessToken> cachedAccessToken = new AtomicReference<>();
    private final TechnicalIndicatorService indicatorService;
    private Mono<AccessToken> tokenRefreshMono;
    private final Mono<List<StockSearchResult>> stockCatalog;

    public KiwoomApiService(
            KiwoomHttpClient client,
            KiwoomResponseMapper mapper,
            KiwoomApiProperties properties,
            MeterRegistry meterRegistry,
            TechnicalIndicatorService indicatorService) {
        this.client = client;
        this.mapper = mapper;
        this.apiKey = properties.key();
        this.apiSecret = properties.secret();
        this.currentPriceCacheTtl = properties.currentPriceCacheTtl();
        this.dailyPriceCacheTtl = properties.dailyPriceCacheTtl();
        this.indicatorService = indicatorService;
        this.currentCacheHits = counter(meterRegistry, "current", "hit");
        this.currentCacheMisses = counter(meterRegistry, "current", "miss");
        this.dailyCacheHits = counter(meterRegistry, "daily", "hit");
        this.dailyApiCalls = Counter.builder("kiwoom.api.daily.calls").register(meterRegistry);
        this.tokenRefreshes = Counter.builder("kiwoom.api.token.refreshes").register(meterRegistry);
        Gauge.builder("kiwoom.cache.entries", currentPriceCache, Map::size)
                .tag("type", "current")
                .register(meterRegistry);
        Gauge.builder("kiwoom.cache.entries", dailyPriceCache, Map::size)
                .tag("type", "daily")
                .register(meterRegistry);
        this.stockCatalog =
                fetchStockCatalog()
                        .cache(
                                value -> Duration.ofHours(12),
                                error -> Duration.ZERO,
                                () -> Duration.ZERO,
                                Schedulers.parallel());
    }

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
        Mono<StockPriceResponse> cached = currentPriceCache.get(normalizedCode);
        if (cached != null) {
            currentCacheHits.increment();
            return cached;
        }
        currentCacheMisses.increment();
        return currentPriceCache.computeIfAbsent(
                normalizedCode,
                key ->
                        fetchStockCurrentPrice(key)
                                .doOnError(error -> currentPriceCache.remove(key))
                                .cache(currentPriceCacheTtl));
    }

    private Mono<StockPriceResponse> fetchStockCurrentPrice(String code) {
        logger.info("stock_price_requested code={}", code);
        return getAccessToken()
                .flatMap(token -> requestStockCurrentPrice(code, token.value()))
                .onErrorResume(
                        KiwoomAuthenticationException.class, error -> retryStockCurrentPrice(code))
                .timeout(Duration.ofSeconds(10))
                .doOnError(
                        error ->
                                logger.warn(
                                        "stock_price_failed code={} errorType={}",
                                        code,
                                        error.getClass().getSimpleName()));
    }

    private Mono<StockPriceResponse> requestStockCurrentPrice(String code, String token) {
        return client.requestCurrentPrice(code, token)
                .map(body -> mapper.parseCurrentPrice(code, body))
                .doOnNext(
                        response ->
                                logger.info("stock_price_succeeded code={}", response.getCode()));
    }

    /** 한 종목이라도 실패하면 전체 요청을 실패시킵니다. */
    public Mono<List<StockPriceResponse>> getMultipleStockPrices(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return Mono.error(new IllegalArgumentException("종목 코드 목록은 필수입니다"));
        }
        final List<String> normalizedCodes;
        try {
            normalizedCodes =
                    List.copyOf(
                            new LinkedHashSet<>(
                                    codes.stream().map(this::normalizeStockCode).toList()));
        } catch (IllegalArgumentException error) {
            return Mono.error(error);
        }
        if (normalizedCodes.size() > MAX_STOCK_CODES) {
            return Mono.error(
                    new IllegalArgumentException(
                            "한 번에 조회할 수 있는 종목은 최대 " + MAX_STOCK_CODES + "개입니다"));
        }
        return Flux.fromIterable(normalizedCodes)
                .flatMap(this::getStockCurrentPrice, 3)
                .collectList();
    }

    public Mono<List<DailyPriceResponse>> getDailyPrices(String code, String baseDate) {
        final String normalizedCode;
        try {
            normalizedCode = normalizeStockCode(code);
        } catch (IllegalArgumentException error) {
            return Mono.error(error);
        }
        String date =
                baseDate == null || baseDate.isBlank()
                        ? LocalDate.now(ZoneId.of("Asia/Seoul"))
                                .format(DateTimeFormatter.BASIC_ISO_DATE)
                        : baseDate.trim();
        try {
            LocalDate.parse(date, DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException error) {
            return Mono.error(new IllegalArgumentException("기준일자는 유효한 yyyyMMdd 날짜여야 합니다"));
        }
        DailyCacheKey key = new DailyCacheKey(normalizedCode, date);
        if (dailyPriceCacheTtl.isZero() || dailyPriceCacheTtl.isNegative()) {
            return fetchDailyPrices(key);
        }
        Mono<List<DailyPriceResponse>> cached = dailyPriceCache.get(key);
        if (cached != null) {
            dailyCacheHits.increment();
            return cached;
        }
        return dailyPriceCache.computeIfAbsent(
                key,
                cacheKey ->
                        fetchDailyPrices(cacheKey)
                                .doOnError(error -> dailyPriceCache.remove(cacheKey))
                                .cache(dailyPriceCacheTtl));
    }

    private Mono<List<DailyPriceResponse>> fetchDailyPrices(DailyCacheKey key) {
        return Mono.defer(
                () -> {
                    dailyApiCalls.increment();
                    return getAccessToken()
                            .flatMap(
                                    token ->
                                            requestDailyPrices(
                                                    key.code(), key.baseDate(), token.value()))
                            .onErrorResume(
                                    KiwoomAuthenticationException.class,
                                    error -> retryDailyPrices(key.code(), key.baseDate()));
                });
    }

    public DailyPriceCacheStats getDailyPriceCacheStats() {
        return new DailyPriceCacheStats(
                (long) dailyCacheHits.count(),
                (long) dailyApiCalls.count(),
                dailyPriceCache.size());
    }

    public Mono<List<StockSearchResult>> searchStocks(String query, String market) {
        if (query == null || query.isBlank()) return Mono.just(List.of());
        String keyword = query.trim().toLowerCase();
        String normalizedMarket =
                market == null || market.isBlank() ? "ALL" : market.trim().toUpperCase();
        if (!List.of("ALL", "KOSPI", "KOSDAQ").contains(normalizedMarket)) {
            return Mono.error(new IllegalArgumentException("시장은 ALL, KOSPI, KOSDAQ 중 하나여야 합니다"));
        }
        return stockCatalog.map(
                items ->
                        items.stream()
                                .filter(
                                        item ->
                                                normalizedMarket.equals("ALL")
                                                        || item.market().equals(normalizedMarket))
                                .filter(
                                        item ->
                                                item.code().contains(keyword)
                                                        || item.name()
                                                                .toLowerCase()
                                                                .contains(keyword))
                                .sorted(
                                        Comparator.comparing(
                                                        (StockSearchResult item) ->
                                                                !item.code().startsWith(keyword))
                                                .thenComparing(
                                                        item ->
                                                                !item.name()
                                                                        .toLowerCase()
                                                                        .startsWith(keyword))
                                                .thenComparing(StockSearchResult::name))
                                .limit(20)
                                .toList());
    }

    private Mono<List<StockSearchResult>> fetchStockCatalog() {
        return getAccessToken()
                .flatMap(token -> requestStockCatalog(token.value()))
                .onErrorResume(
                        KiwoomAuthenticationException.class,
                        error -> {
                            invalidateAccessToken();
                            return getAccessToken()
                                    .flatMap(token -> requestStockCatalog(token.value()));
                        });
    }

    private Mono<List<StockSearchResult>> requestStockCatalog(String token) {
        return Mono.zip(
                requestStockList("0", "KOSPI", token),
                requestStockList("10", "KOSDAQ", token),
                (kospi, kosdaq) -> {
                    List<StockSearchResult> result = new java.util.ArrayList<>(kospi);
                    result.addAll(kosdaq);
                    return List.copyOf(result);
                });
    }

    private Mono<List<StockSearchResult>> requestStockList(
            String marketType, String market, String token) {
        return client.requestStockList(marketType, token)
                .map(body -> mapper.parseStockList(market, body));
    }

    private Mono<List<DailyPriceResponse>> requestDailyPrices(
            String code, String date, String token) {
        logger.info("daily_prices_requested code={} baseDate={}", code, date);
        return client.requestDailyPrices(code, date, token)
                .map(mapper::parseDailyPrices)
                .map(indicatorService::enrich)
                .timeout(Duration.ofSeconds(15));
    }

    private Mono<AccessToken> issueAccessToken() {
        logger.info("kiwoom_token_issue_requested");
        return client.issueAccessToken(apiKey, apiSecret)
                .map(mapper::parseAccessToken)
                .map(parsed -> new AccessToken(parsed.value(), parsed.expiresAt()))
                .timeout(Duration.ofSeconds(10))
                .doOnSuccess(token -> logger.info("kiwoom_token_issue_succeeded"))
                .doOnError(
                        error ->
                                logger.warn(
                                        "kiwoom_token_issue_failed errorType={}",
                                        error.getClass().getSimpleName()));
    }

    private Mono<AccessToken> getAccessToken() {
        return Mono.defer(
                () -> {
                    AccessToken token = cachedAccessToken.get();
                    return token != null && token.isUsable()
                            ? Mono.just(token)
                            : refreshAccessToken();
                });
    }

    private synchronized Mono<AccessToken> refreshAccessToken() {
        AccessToken token = cachedAccessToken.get();
        if (token != null && token.isUsable()) return Mono.just(token);
        if (tokenRefreshMono == null) {
            tokenRefreshes.increment();
            tokenRefreshMono =
                    issueAccessToken()
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
        return getAccessToken().flatMap(token -> requestStockCurrentPrice(code, token.value()));
    }

    private Mono<List<DailyPriceResponse>> retryDailyPrices(String code, String date) {
        invalidateAccessToken();
        return getAccessToken().flatMap(token -> requestDailyPrices(code, date, token.value()));
    }

    private String normalizeStockCode(String code) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("종목 코드는 필수입니다");
        String normalized = code.trim();
        if (!normalized.matches("\\d{6}"))
            throw new IllegalArgumentException("종목 코드는 6자리 숫자여야 합니다");
        return normalized;
    }

    private record AccessToken(String value, Instant expiresAt) {
        private boolean isUsable() {
            return expiresAt.isAfter(Instant.now().plus(TOKEN_REFRESH_MARGIN));
        }
    }

    private record DailyCacheKey(String code, String baseDate) {}

    public record DailyPriceCacheStats(long hits, long apiCalls, int entries) {}

    private Counter counter(MeterRegistry registry, String cache, String result) {
        return Counter.builder("kiwoom.cache.accesses")
                .tag("cache", cache)
                .tag("result", result)
                .register(registry);
    }
}
