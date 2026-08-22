package com.example.kiwoom.repository;

import com.example.kiwoom.dto.StrategyDefinition;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public class StrategyDefinitionRepository {
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};
    private final DatabaseClient database;
    private final ObjectMapper mapper;

    public StrategyDefinitionRepository(DatabaseClient database, ObjectMapper mapper) {
        this.database = database;
        this.mapper = mapper;
    }

    public Flux<StrategyDefinition> findAll() {
        return database.sql("SELECT * FROM strategy_definition ORDER BY strategy_id, version DESC")
                .map(
                        (row, meta) -> {
                            String id = row.get("strategy_id", String.class);
                            int version = ((Number) row.get("version")).intValue();
                            return new StrategyDefinition(
                                    ((Number) row.get("id")).longValue(),
                                    id,
                                    version,
                                    id + "-v" + version,
                                    row.get("name", String.class),
                                    row.get("description", String.class),
                                    row.get("status", String.class),
                                    parameters(row.get("parameters_json", String.class)),
                                    instant(row.get("created_at")));
                        })
                .all();
    }

    private Map<String, Object> parameters(String json) {
        try {
            return mapper.readValue(json, MAP);
        } catch (Exception error) {
            throw new IllegalStateException("전략 파라미터를 읽을 수 없습니다.", error);
        }
    }

    private Instant instant(Object value) {
        return value instanceof Instant i
                ? i
                : value instanceof OffsetDateTime o
                        ? o.toInstant()
                        : Instant.parse(value.toString());
    }
}
