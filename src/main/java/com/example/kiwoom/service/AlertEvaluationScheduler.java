package com.example.kiwoom.service;

import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.alert.scheduler.enabled", havingValue = "true")
public class AlertEvaluationScheduler {
    private static final Logger log = LoggerFactory.getLogger(AlertEvaluationScheduler.class);
    private final AlertService alerts;
    private final MarketCalendarService calendar;
    private final String username;
    private final AtomicBoolean running = new AtomicBoolean();

    public AlertEvaluationScheduler(
            AlertService alerts,
            MarketCalendarService calendar,
            @org.springframework.beans.factory.annotation.Value("${app.alert.scheduler.username}")
                    String username) {
        this.alerts = alerts;
        this.calendar = calendar;
        this.username = username;
    }

    @Scheduled(cron = "${app.alert.scheduler.cron:0 */5 9-15 * * MON-FRI}", zone = "Asia/Seoul")
    public void evaluate() {
        if (!calendar.isOpenNow() || !running.compareAndSet(false, true)) return;
        alerts.evaluate(username)
                .count()
                .doOnNext(
                        count -> log.info("scheduled_alert_evaluation_completed events={}", count))
                .doOnError(
                        error ->
                                log.warn(
                                        "scheduled_alert_evaluation_failed type={}",
                                        error.getClass().getSimpleName()))
                .doFinally(signal -> running.set(false))
                .subscribe();
    }
}
