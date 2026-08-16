package com.example.kiwoom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.example.kiwoom.dto.PortfolioPosition;
import com.example.kiwoom.dto.PortfolioValuation;
import com.example.kiwoom.repository.PortfolioRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PortfolioServiceTest {

    private final PortfolioService portfolioService =
            new PortfolioService(mock(KiwoomApiService.class), mock(PortfolioRepository.class));

    @Test
    void calculatesProfitAndReturnRate() {
        PortfolioPosition position =
                new PortfolioPosition("005930", new BigDecimal("10"), new BigDecimal("70000"));

        PortfolioValuation valuation =
                portfolioService.calculate(position, new BigDecimal("75000"));

        assertThat(valuation.purchaseAmount()).isEqualByComparingTo("700000");
        assertThat(valuation.evaluationAmount()).isEqualByComparingTo("750000");
        assertThat(valuation.profitLoss()).isEqualByComparingTo("50000");
        assertThat(valuation.returnRate()).isEqualByComparingTo("7.14");
    }
}
