package com.example.kiwoom.research.boxevaluation.a1;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.kiwoom.dto.StockSearchResult;
import com.example.kiwoom.repository.HistoricalBackfillRepository;
import com.example.kiwoom.repository.MarketDataRepository;
import com.example.kiwoom.service.HistoricalBackfillStatus;
import java.time.LocalDate;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.r2dbc.core.DatabaseClient;

@SpringBootTest(
        properties = {
            "kiwoom.api.base-url=http://localhost",
            "kiwoom.api.key=test-key",
            "kiwoom.api.secret=test-secret",
            "spring.r2dbc.url=r2dbc:h2:mem:///a1-eligibility;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.url=jdbc:h2:mem:a1-eligibility-flyway;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.username=sa",
            "spring.sql.init.mode=always",
            "spring.flyway.enabled=false"
        })
class A1EligibilityRepositoryTest {
    @Autowired private A1EligibilityRepository eligibility;
    @Autowired private MarketDataRepository marketData;
    @Autowired private HistoricalBackfillRepository backfill;
    @Autowired private DatabaseClient database;

    @Test
    void returnsOnlyTargetReachedSymbolsWithAtLeast252ValidContextCandles() {
        marketData
                .saveStocks(
                        reactor.core.publisher.Flux.just(
                                new StockSearchResult("901001", "eligible", "KOSPI"),
                                new StockSearchResult("901002", "exhausted", "KOSPI"),
                                new StockSearchResult("901003", "other-market", "KONEX"),
                                new StockSearchResult("901004", "too-short", "KOSPI")))
                .block();
        insertCandles("901001", 252);
        insertCandles("901002", 252);
        insertCandles("901003", 252);
        insertCandles("901004", 251);
        backfill.createPending("901001", LocalDate.of(2015, 1, 1)).block();
        backfill.start("901001", LocalDate.of(2015, 1, 1), null).block();
        backfill.finish("901001", HistoricalBackfillStatus.TARGET_REACHED, null).block();
        backfill.createPending("901002", LocalDate.of(2015, 1, 1)).block();
        backfill.start("901002", LocalDate.of(2015, 1, 1), null).block();
        backfill.finish("901002", HistoricalBackfillStatus.HISTORY_EXHAUSTED, null).block();
        backfill.createPending("901003", LocalDate.of(2015, 1, 1)).block();
        backfill.start("901003", LocalDate.of(2015, 1, 1), null).block();
        backfill.finish("901003", HistoricalBackfillStatus.TARGET_REACHED, null).block();
        backfill.createPending("901004", LocalDate.of(2015, 1, 1)).block();
        backfill.start("901004", LocalDate.of(2015, 1, 1), null).block();
        backfill.finish("901004", HistoricalBackfillStatus.TARGET_REACHED, null).block();
        var result = eligibility.findEligibleSymbols().block();
        assertThat(result).extracting(A1EligibleSymbol::code).contains("901001");
        assertThat(result)
                .extracting(A1EligibleSymbol::code)
                .doesNotContain("901002", "901003", "901004");
        assertThat(
                        result.stream()
                                .filter(s -> s.code().equals("901001"))
                                .findFirst()
                                .orElseThrow()
                                .eligibleCutoffDates())
                .hasSize(1);
    }

    private void insertCandles(String code, int count) {
        IntStream.range(0, count)
                .boxed()
                .forEach(
                        index ->
                                database.sql(
                                                """
INSERT INTO daily_candle(code, trade_date, open_price, high_price,
    low_price, close_price, volume)
VALUES (:code, :date, 10, 12, 9, 11, 100)
""")
                                        .bind("code", code)
                                        .bind("date", LocalDate.of(2014, 1, 1).plusDays(index))
                                        .fetch()
                                        .rowsUpdated()
                                        .block());
    }
}
