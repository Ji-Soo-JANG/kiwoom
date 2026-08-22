package com.example.kiwoom.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 서버 재시작과 거래일 경계를 넘어 유지되는 PAPER 스윙 포지션을 장중 감시한다. */
@Component
@ConditionalOnProperty(name = "app.trading.swing-monitor.enabled", havingValue = "true")
public class SwingPositionMonitoringScheduler {
    private static final Logger log =
            LoggerFactory.getLogger(SwingPositionMonitoringScheduler.class);
    private final PaperTradeCycleService cycles;
    private final KiwoomApiService kiwoom;
    private final PaperOrderService orders;
    private final MarketCalendarService calendar;
    private final AtomicBoolean monitoring = new AtomicBoolean();
    private final AtomicBoolean closing = new AtomicBoolean();

    public SwingPositionMonitoringScheduler(
            PaperTradeCycleService cycles,
            KiwoomApiService kiwoom,
            PaperOrderService orders,
            MarketCalendarService calendar) {
        this.cycles = cycles;
        this.kiwoom = kiwoom;
        this.orders = orders;
        this.calendar = calendar;
    }

    @Scheduled(
            cron = "${app.trading.swing-monitor.cron:0 */1 9-15 * * MON-FRI}",
            zone = "Asia/Seoul")
    public void monitor() {
        if (!calendar.isOpenNow() || !monitoring.compareAndSet(false, true)) return;
        cycles.findAll()
                .filter(cycle -> "HOLDING".equals(cycle.status()))
                .distinct(com.example.kiwoom.dto.PaperTradeCycle::code)
                .flatMap(
                        cycle ->
                                kiwoom.getStockCurrentPrice(cycle.code())
                                        .flatMap(
                                                price ->
                                                        cycles.evaluate(
                                                                cycle.code(),
                                                                parsePrice(price.getCurrentPrice()),
                                                                Instant.now())),
                        2)
                .doOnError(
                        error ->
                                log.warn(
                                        "swing_position_monitor_failed type={}",
                                        error.getClass().getSimpleName()))
                .doFinally(signal -> monitoring.set(false))
                .subscribe();
    }

    @Scheduled(
            cron = "${app.trading.swing-monitor.close-cron:0 35 15 * * MON-FRI}",
            zone = "Asia/Seoul")
    public void reconcileAfterClose() {
        if (!closing.compareAndSet(false, true)) return;
        orders.reconcile()
                .zipWith(cycles.summary())
                .doOnNext(
                        result ->
                                log.info(
                                        "paper_swing_close_report reconciliationMatched={} completedTrades={} totalNetPnl={}",
                                        result.getT1().consistent(),
                                        result.getT2().completedTrades(),
                                        result.getT2().totalNetPnl()))
                .doOnError(
                        error ->
                                log.warn(
                                        "paper_swing_close_report_failed type={}",
                                        error.getClass().getSimpleName()))
                .doFinally(signal -> closing.set(false))
                .subscribe();
    }

    private BigDecimal parsePrice(String value) {
        String normalized = value == null ? "" : value.replace(",", "").replace("+", "").trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException("현재가가 비어 있습니다.");
        return new BigDecimal(normalized).abs();
    }
}
