package com.example.kiwoom.service;

import com.example.kiwoom.broker.kiwoom.client.ContinuationToken;
import com.example.kiwoom.broker.kiwoom.client.DailyChartPage;
import com.example.kiwoom.dto.DailyPriceResponse;
import com.example.kiwoom.repository.HistoricalBackfillRepository;
import com.example.kiwoom.repository.MarketDataRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

/** Historical-only candle backfill. It never deletes or rebuilds the latest candle set. */
@Service
public class HistoricalDailyBackfillService {
    private static final DateTimeFormatter BASIC = DateTimeFormatter.BASIC_ISO_DATE;
    private static final int MAX_PAGES_PER_RUN = 10_000;

    private final com.example.kiwoom.service.KiwoomApiService api;
    private final MarketDataRepository candles;
    private final HistoricalBackfillRepository stateRepository;

    public HistoricalDailyBackfillService(
            com.example.kiwoom.service.KiwoomApiService api,
            MarketDataRepository candles,
            HistoricalBackfillRepository stateRepository) {
        this.api = api;
        this.candles = candles;
        this.stateRepository = stateRepository;
    }

    @Transactional(transactionManager = "connectionFactoryTransactionManager")
    public Mono<HistoricalBackfillState> backfill(String code, LocalDate targetStartDate) {
        if (code == null || !code.matches("\\d{6}"))
            return Mono.error(new IllegalArgumentException("code must be six digits"));
        if (targetStartDate == null)
            return Mono.error(new IllegalArgumentException("target date is required"));
        return stateRepository
                .find(code)
                .flatMap(existing -> resumeOrStart(code, targetStartDate, existing))
                .switchIfEmpty(
                        Mono.defer(
                                () ->
                                        candles.findOldestCandleDate(code)
                                                .flatMap(
                                                        oldest ->
                                                                stateRepository
                                                                        .createPending(
                                                                                code,
                                                                                targetStartDate)
                                                                        .then(
                                                                                stateRepository
                                                                                        .start(
                                                                                                code,
                                                                                                targetStartDate,
                                                                                                oldest))
                                                                        .then(
                                                                                stateRepository
                                                                                        .find(
                                                                                                code)))
                                                .switchIfEmpty(
                                                        stateRepository
                                                                .createPending(
                                                                        code, targetStartDate)
                                                                .then(
                                                                        stateRepository.start(
                                                                                code,
                                                                                targetStartDate,
                                                                                null))
                                                                .then(stateRepository.find(code)))))
                .flatMap(state -> traverse(code, targetStartDate, state));
    }

    private Mono<HistoricalBackfillState> resumeOrStart(
            String code, LocalDate target, HistoricalBackfillState existing) {
        if (existing.targetStartDate().equals(target)
                && (existing.status() == HistoricalBackfillStatus.TARGET_REACHED
                        || existing.status() == HistoricalBackfillStatus.ALREADY_SATISFIED
                        || existing.status() == HistoricalBackfillStatus.HISTORY_EXHAUSTED)) {
            return Mono.just(existing);
        }
        if (existing.targetStartDate().equals(target)
                && existing.status() == HistoricalBackfillStatus.IN_PROGRESS) {
            return Mono.just(existing);
        }
        return stateRepository
                .start(code, target, existing.oldestSyncedDate())
                .then(stateRepository.find(code));
    }

    private Mono<HistoricalBackfillState> traverse(
            String code, LocalDate target, HistoricalBackfillState initial) {
        if (initial.status() == HistoricalBackfillStatus.TARGET_REACHED
                || initial.status() == HistoricalBackfillStatus.ALREADY_SATISFIED
                || initial.status() == HistoricalBackfillStatus.HISTORY_EXHAUSTED) {
            return Mono.just(initial);
        }
        LocalDate initialOldest = initial.oldestSyncedDate();
        if (initialOldest != null && !initialOldest.isAfter(target))
            return stateRepository
                    .finish(code, HistoricalBackfillStatus.ALREADY_SATISFIED, null)
                    .then(stateRepository.find(code));
        LocalDate baseDate = initialOldest == null ? LocalDate.now() : initialOldest.minusDays(1);
        // A persisted broker next-key is only an in-run optimization.  A new
        // invocation always anchors the first request at the committed oldest
        // candle date; this keeps resume durable and independent of broker
        // continuation-token lifetime.
        ContinuationToken token = null;
        return traversePage(
                code,
                target,
                baseDate,
                token,
                initial.pageCount(),
                initial.candleCount(),
                initialOldest);
    }

    private Mono<HistoricalBackfillState> traversePage(
            String code,
            LocalDate target,
            LocalDate baseDate,
            ContinuationToken token,
            int pageCount,
            long candleCount,
            LocalDate previousOldest) {
        if (pageCount >= MAX_PAGES_PER_RUN)
            return fail(code, "PAGE_LIMIT", "historical page limit exceeded");
        return api.getDailyChartPage(code, baseDate, token)
                .flatMap(
                        page ->
                                processPage(
                                        code,
                                        target,
                                        baseDate,
                                        token,
                                        page,
                                        pageCount,
                                        candleCount,
                                        previousOldest))
                .onErrorResume(error -> fail(code, "BACKFILL_ERROR", error.getMessage()));
    }

    private Mono<HistoricalBackfillState> processPage(
            String code,
            LocalDate target,
            LocalDate baseDate,
            ContinuationToken token,
            DailyChartPage page,
            int pageCount,
            long candleCount,
            LocalDate previousOldest) {
        List<DailyPriceResponse> values = page.candles();
        try {
            HistoricalCandleValidator.validate(code, baseDate, values);
        } catch (RuntimeException error) {
            return fail(code, "INVALID_PAGE", error.getMessage());
        }
        if (values.isEmpty())
            return checkpointAndFinish(
                    code,
                    previousOldest,
                    null,
                    false,
                    pageCount + 1,
                    candleCount,
                    HistoricalBackfillStatus.HISTORY_EXHAUSTED,
                    HistoricalExhaustionReason.BROKER_HISTORY_EXHAUSTED);
        LocalDate oldest =
                values.stream()
                        .map(v -> LocalDate.parse(v.getDate(), BASIC))
                        .min(Comparator.naturalOrder())
                        .orElse(null);
        if (oldest == null || (previousOldest != null && !oldest.isBefore(previousOldest)))
            return fail(code, "NO_PROGRESS", "historical traversal did not move older");
        int nextPages = pageCount + 1;
        long nextCount = candleCount + values.size();
        return stateRepository
                .persistPage(
                        code,
                        values,
                        oldest,
                        page.continuationToken() == null ? null : page.continuationToken().value(),
                        page.continuationAvailable(),
                        nextPages,
                        nextCount)
                .then(
                        !oldest.isBefore(target)
                                ? (page.continuationAvailable() && page.continuationToken() != null
                                        ? traversePage(
                                                code,
                                                target,
                                                baseDate,
                                                page.continuationToken(),
                                                nextPages,
                                                nextCount,
                                                oldest)
                                        : checkpointAndFinish(
                                                code,
                                                oldest,
                                                null,
                                                false,
                                                nextPages,
                                                nextCount,
                                                HistoricalBackfillStatus.HISTORY_EXHAUSTED,
                                                HistoricalExhaustionReason
                                                        .UNKNOWN_HISTORY_EXHAUSTED))
                                : stateRepository
                                        .finish(code, HistoricalBackfillStatus.TARGET_REACHED, null)
                                        .then(stateRepository.find(code)));
    }

    private Mono<HistoricalBackfillState> checkpointAndFinish(
            String code,
            LocalDate oldest,
            String key,
            boolean active,
            int pages,
            long count,
            HistoricalBackfillStatus status,
            HistoricalExhaustionReason reason) {
        return stateRepository
                .checkpoint(code, oldest, key, active, pages, count)
                .then(stateRepository.finish(code, status, reason))
                .then(stateRepository.find(code));
    }

    private Mono<HistoricalBackfillState> fail(String code, String errorCode, String message) {
        return stateRepository.fail(code, errorCode, message).then(stateRepository.find(code));
    }
}
