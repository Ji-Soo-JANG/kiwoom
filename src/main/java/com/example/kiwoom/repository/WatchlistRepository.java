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

    public Flux<String> findAll(String username) {
        return database.sql("SELECT code FROM watchlist WHERE username = :username ORDER BY code")
                .bind("username", username)
                .map((row, metadata) -> row.get("code", String.class)).all();
    }

    public Mono<Void> add(String username, String code) {
        return database.sql("INSERT INTO watchlist(username, code) VALUES (:username, :code)")
                .bind("username", username).bind("code", code).fetch().rowsUpdated().then()
                .onErrorResume(DuplicateKeyException.class, error -> Mono.empty());
    }

    public Mono<Void> remove(String username, String code) {
        return database.sql("DELETE FROM watchlist WHERE username = :username AND code = :code")
                .bind("username", username).bind("code", code).fetch().rowsUpdated().then();
    }
}
