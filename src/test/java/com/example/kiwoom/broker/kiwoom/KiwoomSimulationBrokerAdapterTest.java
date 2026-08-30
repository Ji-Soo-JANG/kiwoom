package com.example.kiwoom.broker.kiwoom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.kiwoom.dto.OrderSide;
import com.example.kiwoom.dto.PaperOrderRequest;
import com.example.kiwoom.error.TradingSafetyException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class KiwoomSimulationBrokerAdapterTest {
    private final KiwoomSimulationBrokerAdapter adapter = new KiwoomSimulationBrokerAdapter();

    @Test
    void blocksOrderUntilSimulationContractIsVerified() {
        assertThat(adapter.externalSubmissionAvailable()).isFalse();
        assertThatThrownBy(
                        () ->
                                adapter.place(
                                                new PaperOrderRequest(
                                                        "fixture-1",
                                                        "005930",
                                                        OrderSide.BUY,
                                                        1,
                                                        new BigDecimal("70000")))
                                        .block())
                .isInstanceOf(TradingSafetyException.class);
    }
}
