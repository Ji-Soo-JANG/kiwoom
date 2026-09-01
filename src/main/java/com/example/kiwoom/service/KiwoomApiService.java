package com.example.kiwoom.service;

import com.example.kiwoom.broker.kiwoom.client.ContinuationToken;
import com.example.kiwoom.broker.kiwoom.client.DailyChartPage;
import com.example.kiwoom.broker.kiwoom.client.KiwoomHttpClient;
import com.example.kiwoom.broker.kiwoom.mapper.KiwoomResponseMapper;
import com.example.kiwoom.config.KiwoomApiProperties;
import com.example.kiwoom.dto.AccountPortfolioResponse;
import com.example.kiwoom.dto.DailyPriceResponse;
import com.example.kiwoom.dto.MarketRankingItem;
import com.example.kiwoom.dto.MarketRankingsResponse;
import com.example.kiwoom.dto.StockPriceResponse;
import com.example.kiwoom.dto.StockProductType;
import com.example.kiwoom.dto.StockSearchResult;
import com.example.kiwoom.error.KiwoomAuthenticationException;
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
    private static final Map<String, String> PERIOD_API_IDS =
            Map.of(
                    "day", "ka10081",
                    "week", "ka10082",
                    "month", "ka10083",
                    "year", "ka10094");
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
    private final Counter rankingPartialFailures;
    private final Counter accountFailures;
    private final AtomicReference<AccessToken> cachedAccessToken = new AtomicReference<>();
    private final TechnicalIndicatorService indicatorService;
    private Mono<AccessToken> tokenRefreshMono;
    private volatile Mono<List<StockSearchResult>> stockCatalog;
    private volatile Instant stockCatalogRefreshedAt;
    private volatile int stockCatalogSize;
    private volatile Mono<MarketRankingsResponse> marketRankings;
    private volatile Mono<AccountPortfolioResponse> accountPortfolio;

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
        this.rankingPartialFailures =
                Counter.builder("kiwoom.api.ranking.partial_failures").register(meterRegistry);
        this.accountFailures =
                Counter.builder("kiwoom.api.account.failures").register(meterRegistry);
        Gauge.builder("kiwoom.cache.entries", currentPriceCache, Map::size)
                .tag("type", "current")
                .register(meterRegistry);
        Gauge.builder("kiwoom.cache.entries", dailyPriceCache, Map::size)
                .tag("type", "daily")
                .register(meterRegistry);
        this.stockCatalog = createStockCatalog();
        this.marketRankings = createMarketRankings();
        this.accountPortfolio = createAccountPortfolio();
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
        return getPeriodPrices(code, baseDate, 120, "day");
    }

    public Mono<List<DailyPriceResponse>> getDailyPrices(String code, String baseDate, int limit) {
        return getPeriodPrices(code, baseDate, limit, "day");
    }

    /** Fetches a single ka10081 page without discarding broker continuation metadata. */
    public Mono<DailyChartPage> getDailyChartPage(
            String code, LocalDate baseDate, ContinuationToken continuation) {
        final String normalizedCode = normalizeStockCode(code);
        final String date =
                (baseDate == null ? LocalDate.now(ZoneId.of("Asia/Seoul")) : baseDate)
                        .format(DateTimeFormatter.BASIC_ISO_DATE);
        return getAccessToken()
                .flatMap(
                        token ->
                                client.requestDailyChartPage(
                                                normalizedCode, date, continuation, token.value())
                                        .map(
                                                page ->
                                                        new DailyChartPage(
                                                                mapper.parseDailyPrices(
                                                                        page.body()),
                                                                page.continuePaging(),
                                                                new ContinuationToken(
                                                                        page.nextKey()))))
                .onErrorResume(
                        KiwoomAuthenticationException.class,
                        error -> {
                            invalidateAccessToken();
                            return getAccessToken()
                                    .flatMap(
                                            token ->
                                                    client.requestDailyChartPage(
                                                                    normalizedCode,
                                                                    date,
                                                                    continuation,
                                                                    token.value())
                                                            .map(
                                                                    page ->
                                                                            new DailyChartPage(
                                                                                    mapper
                                                                                            .parseDailyPrices(
                                                                                                    page
                                                                                                            .body()),
                                                                                    page
                                                                                            .continuePaging(),
                                                                                    new ContinuationToken(
                                                                                            page
                                                                                                    .nextKey()))));
                        });
    }

    /**
     * @param period 차트 주기: day(일봉), week(주봉), month(월봉), year(년봉). 알 수 없는 값은 일봉으로 처리합니다.
     */
    public Mono<List<DailyPriceResponse>> getPeriodPrices(
            String code, String baseDate, int limit, String period) {
        if (limit < 1 || limit > 500)
            return Mono.error(new IllegalArgumentException("차트 조회 건수는 1부터 500 사이여야 합니다"));
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
        String normalizedPeriod =
                period == null || period.isBlank()
                        ? "day"
                        : period.trim().toLowerCase(java.util.Locale.ROOT);
        String apiId = PERIOD_API_IDS.getOrDefault(normalizedPeriod, "ka10081");
        DailyCacheKey key = new DailyCacheKey(normalizedCode, date, apiId);
        if (dailyPriceCacheTtl.isZero() || dailyPriceCacheTtl.isNegative()) {
            return fetchChartPrices(key).map(prices -> limit(prices, limit));
        }
        Mono<List<DailyPriceResponse>> cached = dailyPriceCache.get(key);
        if (cached != null) {
            dailyCacheHits.increment();
            return cached.map(prices -> limit(prices, limit));
        }
        return dailyPriceCache
                .computeIfAbsent(
                        key,
                        cacheKey ->
                                fetchChartPrices(cacheKey)
                                        .doOnError(error -> dailyPriceCache.remove(cacheKey))
                                        .cache(dailyPriceCacheTtl))
                .map(prices -> limit(prices, limit));
    }

    private List<DailyPriceResponse> limit(List<DailyPriceResponse> prices, int limit) {
        return prices.size() <= limit
                ? prices
                : List.copyOf(prices.subList(prices.size() - limit, prices.size()));
    }

    private Mono<List<DailyPriceResponse>> fetchChartPrices(DailyCacheKey key) {
        return Mono.defer(
                () -> {
                    dailyApiCalls.increment();
                    return getAccessToken()
                            .flatMap(
                                    token ->
                                            requestChartPrices(
                                                    key.code(),
                                                    key.baseDate(),
                                                    key.apiId(),
                                                    token.value()))
                            .onErrorResume(
                                    KiwoomAuthenticationException.class,
                                    error ->
                                            retryChartPrices(
                                                    key.code(), key.baseDate(), key.apiId()));
                });
    }

    public DailyPriceCacheStats getDailyPriceCacheStats() {
        return new DailyPriceCacheStats(
                (long) dailyCacheHits.count(),
                (long) dailyApiCalls.count(),
                dailyPriceCache.size());
    }

    /**
     * 종목 검색을 수행합니다.
     *
     * <p>검색 매칭:
     *
     * <ul>
     *   <li>종목코드에 키워드 포함
     *   <li>종목명(공백/특수문자 제거 후)에 키워드 포함
     *   <li>한글 초성에 키워드 포함
     *   <li>상품유형 검색어(ETF, 리츠, 스팩 등) 매칭
     * </ul>
     *
     * <p>정렬 우선순위 (점수 낮을수록 상위):
     *
     * <ol>
     *   <li>종목코드 정확 일치 (0)
     *   <li>종목명 정확 일치 (1)
     *   <li>종목코드 접두사 일치 (2)
     *   <li>종목명 접두사 또는 한글 초성 접두사 일치 (3)
     *   <li>상품유형 키워드 매칭 (4)
     *   <li>기타 포함 매칭 (5)
     * </ol>
     *
     * @param query 검색 키워드 (종목명, 코드, 초성, 상품유형)
     * @param market 시장 필터: ALL, KOSPI, KOSDAQ
     * @param productType 상품유형 필터: ALL, STOCK, PREFERRED, ETF, ETN, REIT, SPAC
     */
    public Mono<List<StockSearchResult>> searchStocks(
            String query, String market, String productType) {
        if (query == null || query.isBlank()) return Mono.just(List.of());
        String keyword = normalizeSearchText(query);
        String normalizedMarket =
                market == null || market.isBlank() ? "ALL" : market.trim().toUpperCase();
        if (!List.of("ALL", "KOSPI", "KOSDAQ").contains(normalizedMarket)) {
            return Mono.error(new IllegalArgumentException("시장은 ALL, KOSPI, KOSDAQ 중 하나여야 합니다"));
        }
        String normalizedProductType =
                productType == null || productType.isBlank()
                        ? "ALL"
                        : productType.trim().toUpperCase(java.util.Locale.ROOT);
        if (!normalizedProductType.equals("ALL")) {
            try {
                StockProductType.valueOf(normalizedProductType);
            } catch (IllegalArgumentException error) {
                return Mono.error(
                        new IllegalArgumentException(
                                "상품유형은 ALL, STOCK, PREFERRED, ETF, ETN, REIT, SPAC 중 하나여야 합니다"));
            }
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
                                                normalizedProductType.equals("ALL")
                                                        || item.productType()
                                                                .name()
                                                                .equals(normalizedProductType))
                                .filter(
                                        item ->
                                                item.code().contains(keyword)
                                                        || normalizeSearchText(item.name())
                                                                .contains(keyword)
                                                        || koreanInitials(item.name())
                                                                .contains(keyword)
                                                        || item.productType()
                                                                .matchesKeyword(keyword))
                                .sorted(
                                        Comparator.comparingInt(
                                                        (StockSearchResult item) ->
                                                                searchScore(item, keyword))
                                                .thenComparing(
                                                        item -> normalizeSearchText(item.name()))
                                                .thenComparing(StockSearchResult::name))
                                .limit(20)
                                .toList());
    }

    public Mono<List<StockSearchResult>> searchStocks(String query, String market) {
        return searchStocks(query, market, "ALL");
    }

    private String normalizeSearchText(String value) {
        return value.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[\\s·._-]", "");
    }

    private String koreanInitials(String value) {
        String initials = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ";
        StringBuilder result = new StringBuilder();
        for (char character : normalizeSearchText(value).toCharArray()) {
            if (character >= '가' && character <= '힣') {
                result.append(initials.charAt((character - '가') / (21 * 28)));
            } else {
                result.append(character);
            }
        }
        return result.toString();
    }

    /**
     * 검색 결과의 정렬 점수를 계산합니다. 점수가 낮을수록 상위에 표시됩니다.
     *
     * <p>우선순위: 코드 정확일치(0) > 종목명 정확일치(1) > 코드 접두사(2) > 종목명 접두사/초성 접두사(3) > 상품유형 매칭(4) > 기타(5)
     */
    private int searchScore(StockSearchResult item, String keyword) {
        String normalizedName = normalizeSearchText(item.name());
        String initials = koreanInitials(item.name());
        if (item.code().equals(keyword)) return 0;
        if (normalizedName.equals(keyword)) return 1;
        if (item.code().startsWith(keyword)) return 2;
        if (normalizedName.startsWith(keyword) || initials.startsWith(keyword)) return 3;
        if (item.productType().matchesKeyword(keyword)) return 4;
        return 5;
    }

    public synchronized Mono<StockCatalogStatus> refreshStockCatalog() {
        stockCatalog = createStockCatalog();
        return stockCatalog.map(items -> stockCatalogStatus());
    }

    public Mono<List<StockSearchResult>> getStockCatalog() {
        return stockCatalog;
    }

    public StockCatalogStatus stockCatalogStatus() {
        return new StockCatalogStatus(stockCatalogRefreshedAt, stockCatalogSize);
    }

    public Mono<MarketRankingsResponse> getMarketRankings() {
        return marketRankings;
    }

    public Mono<MarketRankingsResponse> getMarketRankings(String market) {
        String normalizedMarket =
                market == null || market.isBlank() ? "ALL" : market.trim().toUpperCase();
        if (!List.of("ALL", "KOSPI", "KOSDAQ").contains(normalizedMarket)) {
            return Mono.error(new IllegalArgumentException("시장은 ALL, KOSPI, KOSDAQ 중 하나여야 합니다"));
        }
        if ("ALL".equals(normalizedMarket)) return getMarketRankings();

        return stockCatalog.zipWith(
                getMarketRankings(),
                (catalog, rankings) -> {
                    java.util.Set<String> marketCodes =
                            catalog.stream()
                                    .filter(item -> normalizedMarket.equals(item.market()))
                                    .map(StockSearchResult::code)
                                    .collect(java.util.stream.Collectors.toSet());
                    return new MarketRankingsResponse(
                            filterRankings(rankings.gainers(), marketCodes),
                            filterRankings(rankings.losers(), marketCodes),
                            filterRankings(rankings.mostTraded(), marketCodes),
                            rankings.updatedAt());
                });
    }

    private List<MarketRankingItem> filterRankings(
            List<MarketRankingItem> rankings, java.util.Set<String> marketCodes) {
        return rankings.stream().filter(item -> marketCodes.contains(item.code())).toList();
    }

    public Mono<AccountPortfolioResponse> getAccountPortfolio() {
        return accountPortfolio;
    }

    private Mono<AccountPortfolioResponse> createAccountPortfolio() {
        return fetchAccountPortfolio()
                .cache(
                        value -> Duration.ofSeconds(10),
                        error -> Duration.ZERO,
                        () -> Duration.ZERO,
                        Schedulers.parallel());
    }

    private Mono<AccountPortfolioResponse> fetchAccountPortfolio() {
        return getAccessToken()
                .flatMap(token -> requestAccountPortfolio(token.value()))
                .onErrorResume(
                        KiwoomAuthenticationException.class,
                        error -> {
                            invalidateAccessToken();
                            return getAccessToken()
                                    .flatMap(token -> requestAccountPortfolio(token.value()));
                        })
                .doOnError(
                        error -> {
                            accountFailures.increment();
                            logger.warn(
                                    "account_portfolio_failed errorType={}",
                                    error.getClass().getSimpleName());
                        });
    }

    private Mono<AccountPortfolioResponse> requestAccountPortfolio(String token) {
        return client.requestAccountNumber(token)
                .map(mapper::parseAccountNumber)
                .zipWith(fetchAccountPortfolioPages(token, null))
                .map(
                        result ->
                                mapper.parseAccountPortfolio(
                                        result.getT1(), result.getT2(), Instant.now()));
    }

    /**
     * cont-yn / next-key 페이징을 순회해 계좌 평가잔고 전체 응답을 조립합니다. 키움 API는 응답 헤더에 cont-yn=Y와 next-key를 포함해 다음
     * 페이지가 있음을 알리며, 최대 10회(안전 한도)까지 반복합니다.
     */
    private Mono<String> fetchAccountPortfolioPages(String token, String nextKey) {
        return client.requestAccountPortfolioPaged(token, nextKey)
                .flatMap(
                        paged -> {
                            if (!paged.continuePaging()
                                    || paged.nextKey() == null
                                    || paged.nextKey().isBlank()) {
                                return Mono.justOrEmpty(paged.body());
                            }
                            return fetchAccountPortfolioPages(token, paged.nextKey())
                                    .map(tail -> mergeBodies(paged.body(), tail));
                        });
    }

    /** 두 키움 응답 JSON의 보유종목 배열을 하나로 합칩니다. */
    private String mergeBodies(String first, String second) {
        // 간단한 JSON 배열 합침: 첫 번째 JSON의 배열 항목에 두 번째의 항목을 추가.
        // 키움 응답 구조가 변경될 수 있으므로 fallback으로 second를 그대로 반환.
        try {
            com.fasterxml.jackson.databind.JsonNode root =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(first);
            com.fasterxml.jackson.databind.JsonNode tail =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(second);
            // 보유종목 배열 키를 찾아서 합침
            for (String key : List.of("acnt_evlt_remn_indv_tot")) {
                com.fasterxml.jackson.databind.JsonNode arr = root.path(key);
                com.fasterxml.jackson.databind.JsonNode tailArr = tail.path(key);
                if (arr.isArray() && tailArr.isArray()) {
                    com.fasterxml.jackson.databind.node.ArrayNode merged =
                            new com.fasterxml.jackson.databind.node.ArrayNode(
                                    com.fasterxml.jackson.databind.node.JsonNodeFactory.instance);
                    arr.forEach(merged::add);
                    tailArr.forEach(merged::add);
                    ((com.fasterxml.jackson.databind.node.ObjectNode) root).set(key, merged);
                    return root.toString();
                }
            }
        } catch (Exception ignored) {
            // 파싱 실패 시 첫 번째 응답만 사용
        }
        return first;
    }

    private Mono<MarketRankingsResponse> createMarketRankings() {
        return fetchMarketRankings()
                .cache(
                        value -> Duration.ofSeconds(30),
                        error -> Duration.ZERO,
                        () -> Duration.ZERO,
                        Schedulers.parallel());
    }

    private Mono<MarketRankingsResponse> fetchMarketRankings() {
        return getAccessToken()
                .flatMap(token -> requestMarketRankings(token.value()))
                .onErrorResume(
                        KiwoomAuthenticationException.class,
                        error -> {
                            invalidateAccessToken();
                            return getAccessToken()
                                    .flatMap(token -> requestMarketRankings(token.value()));
                        });
    }

    private Mono<MarketRankingsResponse> requestMarketRankings(String token) {
        Mono<MarketRankingsResponse> kospi =
                requestMarketRankings("001", token)
                        .onErrorResume(
                                error -> {
                                    rankingPartialFailures.increment();
                                    logger.warn(
                                            "market_rankings_partial_failure market=KOSPI errorType={}",
                                            error.getClass().getSimpleName());
                                    return Mono.just(emptyRankings());
                                });
        Mono<MarketRankingsResponse> kosdaq =
                requestMarketRankings("101", token)
                        .onErrorResume(
                                error -> {
                                    rankingPartialFailures.increment();
                                    logger.warn(
                                            "market_rankings_partial_failure market=KOSDAQ errorType={}",
                                            error.getClass().getSimpleName());
                                    return Mono.just(emptyRankings());
                                });
        return Mono.zip(kospi, kosdaq)
                .map(
                        rankings ->
                                new MarketRankingsResponse(
                                        mergeRankings(
                                                rankings.getT1().gainers(),
                                                rankings.getT2().gainers(),
                                                Comparator.comparingDouble(
                                                                MarketRankingItem::changeRate)
                                                        .reversed()),
                                        mergeRankings(
                                                rankings.getT1().losers(),
                                                rankings.getT2().losers(),
                                                Comparator.comparingDouble(
                                                        MarketRankingItem::changeRate)),
                                        mergeRankings(
                                                rankings.getT1().mostTraded(),
                                                rankings.getT2().mostTraded(),
                                                Comparator.comparingLong(MarketRankingItem::volume)
                                                        .reversed()),
                                        Instant.now()));
    }

    private MarketRankingsResponse emptyRankings() {
        return new MarketRankingsResponse(List.of(), List.of(), List.of(), Instant.now());
    }

    private Mono<MarketRankingsResponse> requestMarketRankings(String marketType, String token) {
        Mono<List<MarketRankingItem>> gainers =
                client.requestChangeRateRanking(marketType, "1", token)
                        .map(body -> mapper.parseRanking("pred_pre_flu_rt_upper", body))
                        .onErrorResume(
                                error -> {
                                    logger.warn(
                                            "ranking_partial_failure type=gainers market={} errorType={}",
                                            marketType,
                                            error.getClass().getSimpleName());
                                    return Mono.just(List.of());
                                });
        Mono<List<MarketRankingItem>> losers =
                client.requestChangeRateRanking(marketType, "2", token)
                        .map(body -> mapper.parseRanking("pred_pre_flu_rt_upper", body))
                        .onErrorResume(
                                error -> {
                                    logger.warn(
                                            "ranking_partial_failure type=losers market={} errorType={}",
                                            marketType,
                                            error.getClass().getSimpleName());
                                    return Mono.just(List.of());
                                });
        Mono<List<MarketRankingItem>> mostTraded =
                client.requestVolumeRanking(marketType, token)
                        .map(body -> mapper.parseRanking("tdy_trde_qty_upper", body))
                        .onErrorResume(
                                error -> {
                                    logger.warn(
                                            "ranking_partial_failure type=mostTraded market={} errorType={}",
                                            marketType,
                                            error.getClass().getSimpleName());
                                    return Mono.just(List.of());
                                });
        return Mono.zip(gainers, losers, mostTraded)
                .map(
                        rankings ->
                                new MarketRankingsResponse(
                                        rankings.getT1(),
                                        rankings.getT2(),
                                        rankings.getT3(),
                                        Instant.now()));
    }

    private List<MarketRankingItem> mergeRankings(
            List<MarketRankingItem> first,
            List<MarketRankingItem> second,
            Comparator<MarketRankingItem> comparator) {
        return java.util.stream.Stream.concat(first.stream(), second.stream())
                .sorted(comparator)
                .limit(10)
                .toList();
    }

    private Mono<List<StockSearchResult>> createStockCatalog() {
        return fetchStockCatalog()
                .doOnNext(
                        items -> {
                            stockCatalogRefreshedAt = Instant.now();
                            stockCatalogSize = items.size();
                        })
                .cache(
                        value -> Duration.ofHours(12),
                        error -> Duration.ZERO,
                        () -> Duration.ZERO,
                        Schedulers.parallel());
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

    private Mono<List<DailyPriceResponse>> requestChartPrices(
            String code, String date, String apiId, String token) {
        logger.info("chart_prices_requested code={} baseDate={} apiId={}", code, date, apiId);
        return client.requestPeriodPrices(code, date, apiId, token)
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

    private Mono<List<DailyPriceResponse>> retryChartPrices(
            String code, String date, String apiId) {
        invalidateAccessToken();
        return getAccessToken()
                .flatMap(token -> requestChartPrices(code, date, apiId, token.value()));
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

    private record DailyCacheKey(String code, String baseDate, String apiId) {}

    public record DailyPriceCacheStats(long hits, long apiCalls, int entries) {}

    public record StockCatalogStatus(Instant refreshedAt, int stockCount) {}

    private Counter counter(MeterRegistry registry, String cache, String result) {
        return Counter.builder("kiwoom.cache.accesses")
                .tag("cache", cache)
                .tag("result", result)
                .register(registry);
    }
}
