package com.example.kiwoom.service.broker;

import com.example.kiwoom.dto.PaperOrderRequest;
import com.example.kiwoom.dto.TradingMode;
import com.example.kiwoom.dto.TradingOrder;
import reactor.core.publisher.Mono;

public interface BrokerAdapter {
    TradingMode mode();

    boolean externalSubmissionAvailable();

    Mono<TradingOrder> place(PaperOrderRequest request);
}
