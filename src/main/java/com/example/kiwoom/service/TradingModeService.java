package com.example.kiwoom.service;

import com.example.kiwoom.config.TradingProperties;
import com.example.kiwoom.dto.TradingMode;
import com.example.kiwoom.dto.TradingModeStatus;
import com.example.kiwoom.error.TradingSafetyException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TradingModeService {
    static final String LIVE_CONFIRMATION = "I_UNDERSTAND_LIVE_TRADING";

    private final TradingProperties properties;

    public TradingModeService(TradingProperties properties) {
        this.properties = properties;
    }

    public TradingModeStatus status() {
        List<String> blockers = new ArrayList<>();
        boolean liveArmed = false;
        TradingMode effective = properties.mode();
        if (properties.mode() == TradingMode.LIVE) {
            if (!properties.liveEnabled()) blockers.add("LIVE_TRADING_ENABLED가 false입니다.");
            if (!LIVE_CONFIRMATION.equals(properties.liveConfirmation())) {
                blockers.add("LIVE_TRADING_CONFIRMATION 확인 문구가 일치하지 않습니다.");
            }
            blockers.add("실주문 브로커 어댑터가 아직 연결되지 않았습니다.");
            liveArmed = blockers.size() == 1;
            effective = TradingMode.SIGNAL_ONLY;
        }
        return new TradingModeStatus(
                properties.mode(), effective, liveArmed, false, List.copyOf(blockers));
    }

    public TradingMode requireOrderCreationMode() {
        TradingModeStatus status = status();
        if (status.effectiveMode() == TradingMode.SIGNAL_ONLY) {
            throw new TradingSafetyException(
                    status.blockers().isEmpty()
                            ? "SIGNAL_ONLY 모드에서는 주문을 만들 수 없습니다."
                            : String.join(" ", status.blockers()));
        }
        return status.effectiveMode();
    }

    public TradingProperties properties() {
        return properties;
    }
}
