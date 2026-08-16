package com.example.kiwoom.mapper;

import com.example.kiwoom.error.KiwoomApiException;
import com.example.kiwoom.error.KiwoomErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KiwoomResponseMapperTest {
    private final KiwoomResponseMapper mapper = new KiwoomResponseMapper(new ObjectMapper());

    @Test
    void rejectsMissingRequiredCurrentPrice() {
        KiwoomApiException error = assertThrows(KiwoomApiException.class,
                () -> mapper.parseCurrentPrice("005930", "{\"return_code\":0,\"stk_cd\":\"005930\"}"));

        assertEquals(KiwoomErrorCode.INVALID_RESPONSE, error.errorCode());
        assertTrue(error.getMessage().contains("cur_prc"));
    }

    @Test
    void rejectsMalformedCurrentPriceNumber() {
        KiwoomApiException error = assertThrows(KiwoomApiException.class,
                () -> mapper.parseCurrentPrice("005930", "{\"return_code\":0,\"cur_prc\":\"75O00\"}"));

        assertEquals(KiwoomErrorCode.INVALID_RESPONSE, error.errorCode());
        assertTrue(error.getMessage().contains("cur_prc"));
    }

    @Test
    void rejectsMissingDailyField() {
        String json = """
                {"return_code":0,"stk_dt_pole_chart_qry":[
                  {"dt":"20260816","open_pric":"70000","high_pric":"71000","low_pric":"69000","cur_prc":"70500"}
                ]}
                """;

        KiwoomApiException error = assertThrows(KiwoomApiException.class, () -> mapper.parseDailyPrices(json));
        assertEquals(KiwoomErrorCode.INVALID_RESPONSE, error.errorCode());
        assertTrue(error.getMessage().contains("trde_qty"));
    }

    @Test
    void rejectsMalformedDailyNumberInsteadOfReplacingItWithZero() {
        String json = """
                {"return_code":0,"stk_dt_pole_chart_qry":[
                  {"dt":"20260816","open_pric":"70x00","high_pric":"71000","low_pric":"69000","cur_prc":"70500","trde_qty":"1000"}
                ]}
                """;

        KiwoomApiException error = assertThrows(KiwoomApiException.class, () -> mapper.parseDailyPrices(json));
        assertEquals(KiwoomErrorCode.INVALID_RESPONSE, error.errorCode());
        assertTrue(error.getMessage().contains("open_pric"));
    }
}
