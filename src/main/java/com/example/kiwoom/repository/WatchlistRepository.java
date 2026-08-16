package com.example.kiwoom.repository;

import com.example.kiwoom.dto.WatchlistItem;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class WatchlistRepository {
    private final DatabaseClient database;

    public WatchlistRepository(DatabaseClient database) {
        this.database = database;
    }

    public Flux<WatchlistItem> findAll(String username) {
        return database.sql(
                        "SELECT code, group_name, note FROM watchlist WHERE username = :username ORDER BY group_name, code")
                .bind("username", username)
                .map(
                        (row, metadata) ->
                                new WatchlistItem(
                                        row.get("code", String.class),
                                        row.get("group_name", String.class),
                                        row.get("note", String.class)))
                .all();
    }

    public Mono<Void> save(String username, WatchlistItem item) {
        return update(username, item)
                .flatMap(
                        updated ->
                                updated > 0
                                        ? Mono.empty()
                                        : database.sql(
                                                        """
                                                INSERT INTO watchlist(username, code, group_name, note)
                                                VALUES (:username, :code, :groupName, :note)
                                                """)
                                                .bind("username", username)
                                                .bind("code", item.code())
                                                .bind("groupName", item.groupName())
                                                .bind("note", item.note())
                                                .fetch()
                                                .rowsUpdated()
                                                .then())
                .onErrorResume(DuplicateKeyException.class, error -> update(username, item).then())
                .then();
    }

    private Mono<Long> update(String username, WatchlistItem item) {
        return database.sql(
                        """
                UPDATE watchlist SET group_name = :groupName, note = :note
                WHERE username = :username AND code = :code
                """)
                .bind("username", username)
                .bind("code", item.code())
                .bind("groupName", item.groupName())
                .bind("note", item.note())
                .fetch()
                .rowsUpdated();
    }

    public Mono<Void> remove(String username, String code) {
        return database.sql("DELETE FROM watchlist WHERE username = :username AND code = :code")
                .bind("username", username)
                .bind("code", code)
                .fetch()
                .rowsUpdated()
                .then();
    }
}
