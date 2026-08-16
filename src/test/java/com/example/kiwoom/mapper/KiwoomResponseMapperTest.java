package com.example.kiwoom.mapper;

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
    void parsesMarketRankingItems() {
        String json =
                """
                {"return_code":0,"pred_pre_flu_rt_upper":[
                  {"stk_cd":"005930","stk_nm":"삼성전자","cur_prc":"+75,000","flu_rt":"+3.25","now_trde_qty":"1234567"}
                ]}
                """;

        var result = mapper.parseRanking("pred_pre_flu_rt_upper", json).getFirst();

        assertEquals("005930", result.code());
        assertEquals(75000, result.currentPrice());
        assertEquals(3.25, result.changeRate());
        assertEquals(1234567, result.volume());
    }
}
