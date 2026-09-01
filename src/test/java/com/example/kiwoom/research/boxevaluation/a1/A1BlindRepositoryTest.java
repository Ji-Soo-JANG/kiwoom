package com.example.kiwoom.research.boxevaluation.a1;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.kiwoom.dto.StockSearchResult;
import com.example.kiwoom.repository.MarketDataRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;

@SpringBootTest(
        properties = {
            "kiwoom.api.base-url=http://localhost",
            "kiwoom.api.key=test-key",
            "kiwoom.api.secret=test-secret",
            "spring.r2dbc.url=r2dbc:h2:mem:///a1-blind;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.url=jdbc:h2:mem:a1-blind-flyway;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.username=sa",
            "spring.sql.init.mode=always",
            "spring.flyway.enabled=false"
        })
class A1BlindRepositoryTest {
    @Autowired private A1BlindRepository repository;
    @Autowired private MarketDataRepository marketData;
    @Autowired private DatabaseClient database;

    @Test
    void blindPayloadContainsOnlyPreCutoffPriceDataWithoutIdentityOrVolume() {
        marketData
                .saveStocks(
                        reactor.core.publisher.Flux.just(
                                new StockSearchResult("902001", "blind", "KOSDAQ")))
                .block();
        long strategy =
                database.sql(
                                """
INSERT INTO strategy_definition(strategy_id, version, name, description,
    status, parameters_json)
VALUES (:id, 1, 'a1', 'a1', 'DRAFT', '{}')
""")
                        .bind("id", "a1-test-" + System.nanoTime())
                        .filter(statement -> statement.returnGeneratedValues("id"))
                        .map(row -> ((Number) row.get("id")).longValue())
                        .one()
                        .block();
        long batch =
                database.sql(
                                """
INSERT INTO box_evaluation_batch(strategy_version_id, name, dataset_version,
    candidate_generator_version, sampling_policy_json, blind_policy_version,
    status, created_by)
VALUES (:strategy, :name, 'a1', 'a1', '{}', 'a1', 'READY', 'test')
""")
                        .bind("strategy", strategy)
                        .bind("name", "a1-" + System.nanoTime())
                        .filter(statement -> statement.returnGeneratedValues("id"))
                        .map(row -> ((Number) row.get("id")).longValue())
                        .one()
                        .block();
        long item =
                database.sql(
                                """
INSERT INTO box_evaluation_item(batch_id, code, cutoff_date, display_order,
    data_hash, status)
VALUES (:batch, '902001', :cutoff, 1, 'hash', 'PENDING')
""")
                        .bind("batch", batch)
                        .bind("cutoff", LocalDate.of(2020, 1, 2))
                        .filter(statement -> statement.returnGeneratedValues("id"))
                        .map(row -> ((Number) row.get("id")).longValue())
                        .one()
                        .block();
        insertCandle("902001", LocalDate.of(2020, 1, 1));
        insertCandle("902001", LocalDate.of(2020, 1, 2));
        insertCandle("902001", LocalDate.of(2020, 1, 3));
        A1BlindPayload payload = repository.findPayload(item).block();
        assertThat(payload.cutoffDate()).isEqualTo(LocalDate.of(2020, 1, 2));
        assertThat(payload.candles()).hasSize(2);
        assertThat(payload.candles())
                .allSatisfy(
                        candle ->
                                assertThat(candle.tradeDate())
                                        .isBeforeOrEqualTo(payload.cutoffDate()));
        assertThat(payload.toString()).doesNotContain("902001", "blind", "volume");
    }

    private void insertCandle(String code, LocalDate date) {
        database.sql(
                        """
                        INSERT INTO daily_candle(code, trade_date, open_price, high_price,
                            low_price, close_price, volume)
                        VALUES (:code, :date, 10, 12, 9, 11, 100)
                        """)
                .bind("code", code)
                .bind("date", date)
                .fetch()
                .rowsUpdated()
                .block();
    }
}
