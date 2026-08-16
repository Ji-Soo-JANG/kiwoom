package com.example.kiwoom.repository;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class WatchlistRepository {
    private final DatabaseClient database;

    public WatchlistRepository(DatabaseClient database) { this.database = database; }

    public Flux<String> findAll() {
        return database.sql("SELECT code FROM watchlist ORDER BY code")
                .map((row, metadata) -> row.get("code", String.class)).all();
    }

    public Mono<Void> add(String code) {
        return database.sql("INSERT INTO watchlist(code) VALUES (:code)")
                .bind("code", code).fetch().rowsUpdated().then()
                .onErrorResume(DuplicateKeyException.class, error -> Mono.empty());
    }

    public Mono<Void> remove(String code) {
        return database.sql("DELETE FROM watchlist WHERE code = :code")
                .bind("code", code).fetch().rowsUpdated().then();
    }
}
