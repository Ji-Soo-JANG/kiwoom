package com.example.kiwoom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.kiwoom.broker.kiwoom.mapper.KiwoomResponseMapper;
import com.example.kiwoom.dto.StockProductType;
import com.example.kiwoom.error.KiwoomApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** 키움 API 실응답 기반 계약 테스트. 픽스처 JSON을 KiwoomResponseMapper로 파싱해 DTO 필드 변경 시 즉시 실패하도록 합니다. */
@DisplayName("키움 응답 계약 테스트")
class KiwoomResponseContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final KiwoomResponseMapper mapper = new KiwoomResponseMapper(objectMapper);

    private String loadFixture(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("계좌 평가잔고 성공 응답을 파싱한다")
    void parsesAccountPortfolioSuccess() throws IOException {
        String json = loadFixture("kiwoom-fixtures/account-portfolio-success.json");

        var result = mapper.parseAccountPortfolio("123-456-78901", json, Instant.now());

        assertThat(result.accountNumber()).isNotBlank();
        assertThat(result.totalPurchaseAmount()).isEqualTo(700000);
        assertThat(result.totalEvaluationAmount()).isEqualTo(750000);
        assertThat(result.totalProfitLoss()).isEqualTo(50000);
        assertThat(result.totalReturnRate()).isEqualTo(7.14);
        assertThat(result.positions()).hasSize(1);
        assertThat(result.positions().getFirst().code()).isEqualTo("005930");
        assertThat(result.positions().getFirst().name()).isEqualTo("삼성전자");
        assertThat(result.positions().getFirst().quantity()).isEqualTo(10);
        assertThat(result.positions().getFirst().evaluationAmount()).isEqualTo(750000);
        assertThat(result.positions().getFirst().profitLoss()).isEqualTo(50000);
        assertThat(result.positions().getFirst().weight()).isGreaterThan(0);
        assertThat(result.positions().getFirst().profitContribution()).isGreaterThan(0);
    }

    @Test
    @DisplayName("빈 보유종목 계좌 응답을 파싱한다")
    void parsesAccountPortfolioEmpty() throws IOException {
        String json = loadFixture("kiwoom-fixtures/account-portfolio-empty.json");

        var result = mapper.parseAccountPortfolio("123-456-78901", json, Instant.now());

        assertThat(result.accountNumber()).isNotBlank();
        assertThat(result.positions()).isEmpty();
        assertThat(result.totalPurchaseAmount()).isEqualTo(0);
    }

    @Test
    @DisplayName("급등 순위 응답을 파싱한다")
    void parsesRankingGainers() throws IOException {
        String json = loadFixture("kiwoom-fixtures/ranking-gainers.json");

        var result = mapper.parseRanking("pred_pre_flu_rt_upper", json);

        assertThat(result).hasSize(2);
        assertThat(result.getFirst().code()).isEqualTo("035720");
        assertThat(result.getFirst().name()).isEqualTo("카카오");
        assertThat(result.getFirst().currentPrice()).isEqualTo(50000);
        assertThat(result.getFirst().changeRate()).isEqualTo(2.50);
        assertThat(result.getFirst().volume()).isEqualTo(1234567);
    }

    @Test
    @DisplayName("코스피 종목 목록 응답을 파싱한다")
    void parsesStockListKospi() throws IOException {
        String json = loadFixture("kiwoom-fixtures/stock-list-kospi.json");

        var result = mapper.parseStockList("KOSPI", json);

        assertThat(result).hasSize(7);
        assertThat(result.getFirst().code()).isEqualTo("005930");
        assertThat(result.getFirst().name()).isEqualTo("삼성전자");
        assertThat(result.getFirst().market()).isEqualTo("KOSPI");
        // ETF 분류 검증
        var etf = result.stream().filter(r -> r.code().equals("069500")).findFirst();
        assertThat(etf).isPresent();
        assertThat(etf.get().productType()).isEqualTo(StockProductType.ETF);
        // 우선주 분류 검증
        var preferred = result.stream().filter(r -> r.code().equals("000815")).findFirst();
        assertThat(preferred).isPresent();
        assertThat(preferred.get().productType()).isEqualTo(StockProductType.PREFERRED);
        // 스팩 분류 검증
        var spac = result.stream().filter(r -> r.code().equals("475250")).findFirst();
        assertThat(spac).isPresent();
        assertThat(spac.get().productType()).isEqualTo(StockProductType.SPAC);
        // 리츠 분류 검증
        var reit = result.stream().filter(r -> r.code().equals("365550")).findFirst();
        assertThat(reit).isPresent();
        assertThat(reit.get().productType()).isEqualTo(StockProductType.REIT);
    }

    @Test
    @DisplayName("키움 에러 응답은 KiwoomApiException으로 변환한다")
    void parsesErrorResponse() {
        String json = "{\"return_code\":100,\"return_msg\":\"종목 없음\"}";

        assertThatThrownBy(() -> mapper.parseCurrentPrice("005930", json))
                .isInstanceOf(KiwoomApiException.class);
    }

    @Test
    @DisplayName("계좌번호 마스킹이 올바르게 동작한다")
    void masksAccountNumber() {
        assertThat(KiwoomResponseMapper.maskAccountNumber("123-456-78901"))
                .isEqualTo("123-***-**01");
        assertThat(KiwoomResponseMapper.maskAccountNumber("1234567890")).isEqualTo("123-***-**90");
        assertThat(KiwoomResponseMapper.maskAccountNumber("")).isEqualTo("");
        assertThat(KiwoomResponseMapper.maskAccountNumber(null)).isNull();
    }
}
