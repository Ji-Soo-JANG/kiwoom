package com.example.kiwoom.service;

import static org.junit.jupiter.api.Assertions.*;

import com.example.kiwoom.dto.DailyPriceResponse;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TechnicalIndicatorServiceTest {
    private final TechnicalIndicatorService service = new TechnicalIndicatorService();

    @Test
    void calculatesRsiAndMacdWithoutChangingPriceOrder() {
        List<DailyPriceResponse> prices = new ArrayList<>();
        for (int index = 0; index < 40; index++) {
            long close = 100 + index + (index % 3 == 0 ? -2 : 1);
            prices.add(
                    new DailyPriceResponse(
                            String.format("202601%02d", index + 1), close, close, close, close, 1));
        }

        List<DailyPriceResponse> result = service.enrich(prices);

        assertEquals("20260101", result.get(0).getDate());
        assertNull(result.get(13).getRsi());
        assertNotNull(result.get(14).getRsi());
        assertNotNull(result.get(25).getMacd());
        assertNotNull(result.get(33).getSignal());
        assertTrue(result.get(39).getRsi() >= 0 && result.get(39).getRsi() <= 100);
    }
}
