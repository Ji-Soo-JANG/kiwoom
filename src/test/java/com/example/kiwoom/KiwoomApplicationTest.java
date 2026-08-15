package com.example.kiwoom;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "kiwoom.api.base-url=http://localhost",
        "kiwoom.api.key=test-key",
        "kiwoom.api.secret=test-secret",
        "kiwoom.api.connect-timeout=1s",
        "kiwoom.api.response-timeout=2s",
        "kiwoom.api.max-connections=5",
        "kiwoom.api.max-retries=2",
        "kiwoom.api.retry-backoff=1ms"
})
class KiwoomApplicationTest {

    @Test
    void contextLoads() {
    }
}
