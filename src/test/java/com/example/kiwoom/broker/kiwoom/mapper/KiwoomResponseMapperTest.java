package com.example.kiwoom.broker.kiwoom.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.kiwoom.dto.StockProductType;
import com.example.kiwoom.error.KiwoomApiException;
import com.example.kiwoom.error.KiwoomErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class KiwoomResponseMapperTest {
    private final KiwoomResponseMapper mapper = new KiwoomResponseMapper(new ObjectMapper());

    @Test
    void rejectsMissingRequiredCurrentPrice() {
        KiwoomApiException error =
                assertThrows(
                        KiwoomApiException.class,
                        () ->
                                mapper.parseCurrentPrice(
                                        "005930", "{\"return_code\":0,\"stk_cd\":\"005930\"}"));

        assertEquals(KiwoomErrorCode.INVALID_RESPONSE, error.errorCode());
        assertTrue(error.getMessage().contains("cur_prc"));
    }

    @Test
    void rejectsMalformedCurrentPriceNumber() {
        KiwoomApiException error =
                assertThrows(
                        KiwoomApiException.class,
                        () ->
                                mapper.parseCurrentPrice(
                                        "005930", "{\"return_code\":0,\"cur_prc\":\"75O00\"}"));

        assertEquals(KiwoomErrorCode.INVALID_RESPONSE, error.errorCode());
        assertTrue(error.getMessage().contains("cur_prc"));
    }

    @Test
    void rejectsMissingDailyField() {
        String json =
                """
                {"return_code":0,"stk_dt_pole_chart_qry":[
                  {"dt":"20260816","open_pric":"70000","high_pric":"71000","low_pric":"69000","cur_prc":"70500"}
                ]}
                """;

        KiwoomApiException error =
                assertThrows(KiwoomApiException.class, () -> mapper.parseDailyPrices(json));
        assertEquals(KiwoomErrorCode.INVALID_RESPONSE, error.errorCode());
        assertTrue(error.getMessage().contains("trde_qty"));
    }

    @Test
    void rejectsMalformedDailyNumberInsteadOfReplacingItWithZero() {
        String json =
                """
                {"return_code":0,"stk_dt_pole_chart_qry":[
                  {"dt":"20260816","open_pric":"70x00","high_pric":"71000","low_pric":"69000","cur_prc":"70500","trde_qty":"1000"}
                ]}
                """;

        KiwoomApiException error =
                assertThrows(KiwoomApiException.class, () -> mapper.parseDailyPrices(json));
        assertEquals(KiwoomErrorCode.INVALID_RESPONSE, error.errorCode());
        assertTrue(error.getMessage().contains("open_pric"));
    }

    @Test
    void ignoresAllBlankDailyNoDataPlaceholder() {
        String json =
                """
                {"return_code":0,"stk_dt_pole_chart_qry":[
                  {"dt":"","open_pric":"","high_pric":"","low_pric":"","cur_prc":"","trde_qty":""}
                ]}
                """;

        assertTrue(mapper.parseDailyPrices(json).isEmpty());
    }

    @Test
    void rejectsPartiallyBlankDailyRow() {
        String json =
                """
                {"return_code":0,"stk_dt_pole_chart_qry":[
                  {"dt":"","open_pric":"100","high_pric":"100","low_pric":"100","cur_prc":"100","trde_qty":"1"}
                ]}
                """;

        KiwoomApiException error =
                assertThrows(KiwoomApiException.class, () -> mapper.parseDailyPrices(json));
        assertEquals(KiwoomErrorCode.INVALID_RESPONSE, error.errorCode());
        assertTrue(error.getMessage().contains("dt"));
    }

    @Test
    void classifiesProductTypesInStockCatalog() {
        String json =
                """
                {"return_code":0,"list":[
                  {"code":"005930","name":"삼성전자"},
                  {"code":"069500","name":"KODEX 200"},
                  {"code":"530001","name":"삼성 코스피200 ETN"},
                  {"code":"088980","name":"맥쿼리인프라"},
                  {"code":"365550","name":"ESR켄달스퀘어리츠"},
                  {"code":"000815","name":"삼성화재우"},
                  {"code":"475250","name":"하나31호스팩"}
                ]}
                """;

        var results = mapper.parseStockList("KOSPI", json);

        assertEquals(StockProductType.STOCK, results.get(0).productType());
        assertEquals(StockProductType.ETF, results.get(1).productType());
        assertEquals(StockProductType.ETN, results.get(2).productType());
        assertEquals(StockProductType.STOCK, results.get(3).productType());
        assertEquals(StockProductType.REIT, results.get(4).productType());
        assertEquals(StockProductType.PREFERRED, results.get(5).productType());
        assertEquals(StockProductType.SPAC, results.get(6).productType());
    }

    @Test
    void classifiesProductTypeByCodePattern() {
        // ETN 코드 대역(50-59) 테스트
        assertEquals(StockProductType.ETN, StockProductType.classify("코스피200", "500001"));
        assertEquals(StockProductType.ETN, StockProductType.classify("S&P500 ETN", "530001"));
        // 코드가 없으면 종목명만으로 분류
        assertEquals(StockProductType.STOCK, StockProductType.classify("삼성전자", null));
        assertEquals(StockProductType.STOCK, StockProductType.classify("삼성전자", ""));
    }

    @Test
    void classifiesProductTypeByKeywordInName() {
        // 상품유형 키워드 매칭
        assertEquals(StockProductType.ETF, StockProductType.classify("TIGER 200"));
        assertEquals(StockProductType.ETF, StockProductType.classify("ACE ETF"));
        assertEquals(StockProductType.ETN, StockProductType.classify("삼성 ETN"));
        assertEquals(StockProductType.REIT, StockProductType.classify("이리츠"));
        assertEquals(StockProductType.REIT, StockProductType.classify("리츠비즈"));
        assertEquals(StockProductType.SPAC, StockProductType.classify("하나스팩"));
        assertEquals(StockProductType.SPAC, StockProductType.classify("KB기업인수목적"));
    }

    @Test
    void parsesMarketRankingItems() {
        String json =
                """
                {"return_code":0,"pred_pre_flu_rt_upper":[
                  {"stk_cd":"A005930_AL","stk_nm":"삼성전자","cur_prc":"+75,000","flu_rt":"+3.25","now_trde_qty":"1234567"}
                ]}
                """;

        var result = mapper.parseRanking("pred_pre_flu_rt_upper", json).getFirst();

        assertEquals("005930", result.code());
        assertEquals(75000, result.currentPrice());
        assertEquals(3.25, result.changeRate());
        assertEquals(1234567, result.volume());
    }

    @Test
    void parsesAccountNumberAndPortfolio() {
        assertEquals(
                "1234567890",
                mapper.parseAccountNumber("{\"return_code\":0,\"acctNo\":\"1234567890\"}"));
        String json =
                """
                {"return_code":0,"tot_pur_amt":"700000","tot_evlt_amt":"750000",
                 "tot_evlt_pl":"+50000","tot_prft_rt":"7.14","prsm_dpst_aset_amt":"1000000",
                 "acnt_evlt_remn_indv_tot":[
                   {"stk_cd":"A005930_AL","stk_nm":"삼성전자","rmnd_qty":"10",
                    "trde_able_qty":"10","pur_pric":"70000","cur_prc":"+75000",
                    "pur_amt":"700000","evlt_amt":"750000","evltv_prft":"+50000","prft_rt":"7.14"}
                 ]}
                """;

        var result = mapper.parseAccountPortfolio("1234567890", json, java.time.Instant.EPOCH);

        assertEquals(750000, result.totalEvaluationAmount());
        assertEquals("005930", result.positions().getFirst().code());
        assertEquals(10, result.positions().getFirst().quantity());
        assertEquals(50000, result.positions().getFirst().profitLoss());
    }
}
