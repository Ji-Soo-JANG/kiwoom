package com.example.kiwoom.service;

import com.example.kiwoom.dto.AutoTradingControl;
import com.example.kiwoom.dto.AutoTradingControlRequest;
import com.example.kiwoom.error.TradingSafetyException;
import com.example.kiwoom.repository.AutoTradingControlRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class AutoTradingControlService {
    public static final String LIVE_CONFIRMATION = "ENABLE_BLOCKED_LIVE_AUTOMATION";
    public static final String DEFAULT_STRATEGY = "drop-base-breakout-pullback-v1";
    private static final List<String> STRATEGIES = List.of(DEFAULT_STRATEGY);
    private static final List<String> LIVE_BLOCKERS = List.of("실주문 브로커 어댑터가 연결되지 않아 주문 전송은 차단됩니다.");
    private final AutoTradingControlRepository repository;

    public AutoTradingControlService(AutoTradingControlRepository repository) {
        this.repository = repository;
    }

    public Mono<AutoTradingControl> get() {
        return repository.get().map(this::map);
    }

    public Mono<Boolean> paperEnabledFor(String strategy) {
        return repository.get().map(c -> c.paperEnabled() && c.paperStrategy().equals(strategy));
    }

    public Mono<AutoTradingControl> update(AutoTradingControlRequest request, String user) {
        validateStrategy(request.paperStrategy());
        validateStrategy(request.liveStrategy());
        if (request.liveEnabled() && !LIVE_CONFIRMATION.equals(request.liveConfirmation())) {
            return Mono.error(new TradingSafetyException("실투자 자동매매 확인 문구가 일치하지 않습니다."));
        }
        return repository
                .update(
                        request.paperEnabled(),
                        request.paperStrategy(),
                        request.liveEnabled(),
                        request.liveStrategy(),
                        user)
                .map(this::map);
    }

    private void validateStrategy(String strategy) {
        if (!STRATEGIES.contains(strategy))
            throw new TradingSafetyException("지원하지 않는 전략입니다: " + strategy);
    }

    private AutoTradingControl map(AutoTradingControlRepository.StoredControl c) {
        return new AutoTradingControl(
                c.paperEnabled(),
                c.paperStrategy(),
                c.liveEnabled(),
                c.liveStrategy(),
                false,
                STRATEGIES,
                c.liveEnabled() ? LIVE_BLOCKERS : List.of(),
                c.updatedBy(),
                c.updatedAt());
    }
}
