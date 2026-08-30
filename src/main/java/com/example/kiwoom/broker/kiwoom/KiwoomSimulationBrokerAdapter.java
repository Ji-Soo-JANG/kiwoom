package com.example.kiwoom.broker.kiwoom;

import com.example.kiwoom.broker.BrokerAdapter;
import com.example.kiwoom.dto.PaperOrderRequest;
import com.example.kiwoom.dto.TradingMode;
import com.example.kiwoom.dto.TradingOrder;
import com.example.kiwoom.error.TradingSafetyException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class KiwoomSimulationBrokerAdapter implements BrokerAdapter {
    @Override
    public TradingMode mode() {
        return TradingMode.LIVE;
    }

    @Override
    public boolean externalSubmissionAvailable() {
        return false;
    }

    @Override
    public Mono<TradingOrder> place(PaperOrderRequest request) {
        return Mono.error(new TradingSafetyException("키움 모의투자 주문 어댑터는 픽스처 검증 전까지 차단됩니다."));
    }
}
