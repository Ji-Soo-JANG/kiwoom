package com.example.kiwoom.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class LocalConfigurationDiagnostics implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(LocalConfigurationDiagnostics.class);

    private final KiwoomApiProperties kiwoom;
    private final String r2dbcUrl;
    private final String username;
    private final String loginPassword;

    public LocalConfigurationDiagnostics(
            KiwoomApiProperties kiwoom,
            @Value("${spring.r2dbc.url}") String r2dbcUrl,
            @Value("${app.security.username}") String username,
            @Value("${app.security.password}") String loginPassword) {
        this.kiwoom = kiwoom;
        this.r2dbcUrl = r2dbcUrl;
        this.username = username;
        this.loginPassword = loginPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        rejectPlaceholder("KIWOOM_APP_KEY", kiwoom.key());
        rejectPlaceholder("KIWOOM_SECRET_KEY", kiwoom.secret());
        if ("change-me".equals(loginPassword)) {
            throw new IllegalStateException("APP_PASSWORD가 기본값입니다. 로컬 전용 비밀번호를 명시적으로 설정하세요.");
        }
        log.info(
                "local_configuration_ready profile=dev kiwoomBaseUrl={} database={} loginUser={} credentials=present",
                kiwoom.baseUrl(),
                sanitizeDatabaseUrl(r2dbcUrl),
                username);
    }

    private void rejectPlaceholder(String name, String value) {
        if (value == null || value.isBlank() || value.startsWith("replace-")) {
            throw new IllegalStateException(name + "에 실제 로컬 값을 설정하세요.");
        }
    }

    private String sanitizeDatabaseUrl(String value) {
        int queryIndex = value.indexOf('?');
        return queryIndex < 0 ? value : value.substring(0, queryIndex);
    }
}
