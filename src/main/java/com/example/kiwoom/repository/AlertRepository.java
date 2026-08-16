package com.example.kiwoom.repository;

import com.example.kiwoom.dto.*;
import io.r2dbc.spi.Row;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Repository
public class AlertRepository {
    private final DatabaseClient database;

    public AlertRepository(DatabaseClient database) { this.database = database; }

    public Flux<AlertRule> findRules(String username) {
        return database.sql("""
                SELECT id, code, condition_type, threshold, enabled, last_state
                FROM alert_rule WHERE username = :username ORDER BY id
                """).bind("username", username).map((row, metadata) -> mapRule(row)).all();
    }

    public Flux<AlertRule> findEnabledRules(String username) {
        return database.sql("""
                SELECT id, code, condition_type, threshold, enabled, last_state
                FROM alert_rule WHERE username = :username AND enabled = TRUE ORDER BY id
                """).bind("username", username).map((row, metadata) -> mapRule(row)).all();
    }

    public Mono<AlertRule> findRule(String username, long id) {
        return database.sql("""
                SELECT id, code, condition_type, threshold, enabled, last_state
                FROM alert_rule WHERE username = :username AND id = :id
                """).bind("username", username).bind("id", id)
                .map((row, metadata) -> mapRule(row)).one();
    }

    public Mono<AlertRule> addRule(String username, AlertRuleRequest request) {
        DatabaseClient.GenericExecuteSpec spec = database.sql("""
                INSERT INTO alert_rule(username, code, condition_type, threshold)
                VALUES (:username, :code, :type, :threshold)
                """).bind("username", username).bind("code", request.code())
                .bind("type", request.conditionType().name());
        spec = request.threshold() == null
                ? spec.bindNull("threshold", BigDecimal.class) : spec.bind("threshold", request.threshold());
        return spec
                .filter(statement -> statement.returnGeneratedValues("id"))
                .map(row -> new AlertRule(row.get("id", Long.class), request.code(),
                        request.conditionType(), request.threshold(), true, false)).one()
                .onErrorMap(DuplicateKeyException.class,
                        error -> new IllegalArgumentException("동일한 목표가 알림 규칙이 이미 존재합니다"));
    }

    public Mono<AlertRule> updateRule(String username, long id, BigDecimal threshold, boolean enabled) {
        DatabaseClient.GenericExecuteSpec spec = database.sql("""
                UPDATE alert_rule SET threshold = :threshold, enabled = :enabled,
                  last_state = FALSE, updated_at = CURRENT_TIMESTAMP
                WHERE username = :username AND id = :id
                """).bind("enabled", enabled).bind("username", username).bind("id", id);
        spec = threshold == null ? spec.bindNull("threshold", BigDecimal.class) : spec.bind("threshold", threshold);
        return spec
                .fetch().rowsUpdated().flatMap(updated -> updated > 0
                        ? findRule(username, id) : Mono.empty())
                .onErrorMap(DuplicateKeyException.class,
                        error -> new IllegalArgumentException("동일한 목표가 알림 규칙이 이미 존재합니다"));
    }

    public Mono<Boolean> transitionToTriggered(String username, long id) {
        return database.sql("""
                UPDATE alert_rule SET last_state = TRUE, updated_at = CURRENT_TIMESTAMP
                WHERE username = :username AND id = :id AND last_state = FALSE
                """).bind("username", username).bind("id", id).fetch().rowsUpdated()
                .map(updated -> updated > 0);
    }

    public Mono<Void> resetState(String username, long id) {
        return database.sql("""
                UPDATE alert_rule SET last_state = FALSE, updated_at = CURRENT_TIMESTAMP
                WHERE username = :username AND id = :id AND last_state = TRUE
                """).bind("username", username).bind("id", id).fetch().rowsUpdated().then();
    }

    public Mono<Void> deleteRule(String username, long id) {
        return database.sql("DELETE FROM alert_rule WHERE username = :username AND id = :id")
                .bind("username", username).bind("id", id).fetch().rowsUpdated().then();
    }

    public Mono<AlertEvent> addEvent(String username, AlertRule rule, BigDecimal observedValue) {
        OffsetDateTime triggeredAt = OffsetDateTime.now();
        DatabaseClient.GenericExecuteSpec spec = database.sql("""
                INSERT INTO alert_event(rule_id, username, code, condition_type,
                  observed_value, threshold, triggered_at)
                VALUES (:ruleId, :username, :code, :type, :observed, :threshold, :triggeredAt)
                """).bind("ruleId", rule.id()).bind("username", username).bind("code", rule.code())
                .bind("type", rule.conditionType().name()).bind("observed", observedValue)
                .bind("triggeredAt", triggeredAt);
        spec = rule.threshold() == null
                ? spec.bindNull("threshold", BigDecimal.class) : spec.bind("threshold", rule.threshold());
        return spec
                .filter(statement -> statement.returnGeneratedValues("id"))
                .map(row -> new AlertEvent(row.get("id", Long.class), rule.id(), rule.code(),
                        rule.conditionType(), observedValue, rule.threshold(), triggeredAt, null)).one();
    }

    public Flux<AlertEvent> findEvents(String username, boolean unreadOnly) {
        String condition = unreadOnly ? " AND read_at IS NULL" : "";
        return database.sql("""
                SELECT id, rule_id, code, condition_type, observed_value, threshold, triggered_at, read_at
                FROM alert_event WHERE username = :username
                """ + condition + " ORDER BY triggered_at DESC, id DESC")
                .bind("username", username).map((row, metadata) -> mapEvent(row)).all();
    }

    public Mono<AlertEvent> findEvent(String username, long id) {
        return database.sql("""
                SELECT id, rule_id, code, condition_type, observed_value, threshold, triggered_at, read_at
                FROM alert_event WHERE username = :username AND id = :id
                """).bind("username", username).bind("id", id)
                .map((row, metadata) -> mapEvent(row)).one();
    }

    public Mono<Void> markRead(String username, long id) {
        return database.sql("""
                UPDATE alert_event SET read_at = CURRENT_TIMESTAMP
                WHERE username = :username AND id = :id
                """).bind("username", username).bind("id", id).fetch().rowsUpdated().then();
    }

    private AlertRule mapRule(Row row) {
        return new AlertRule(row.get("id", Long.class), row.get("code", String.class),
                AlertConditionType.valueOf(row.get("condition_type", String.class)),
                row.get("threshold", BigDecimal.class), Boolean.TRUE.equals(row.get("enabled", Boolean.class)),
                Boolean.TRUE.equals(row.get("last_state", Boolean.class)));
    }

    private AlertEvent mapEvent(Row row) {
        return new AlertEvent(row.get("id", Long.class), row.get("rule_id", Long.class),
                row.get("code", String.class), AlertConditionType.valueOf(row.get("condition_type", String.class)),
                row.get("observed_value", BigDecimal.class), row.get("threshold", BigDecimal.class),
                row.get("triggered_at", OffsetDateTime.class), row.get("read_at", OffsetDateTime.class));
    }
}
