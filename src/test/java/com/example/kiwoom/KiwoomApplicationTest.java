package com.example.kiwoom;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "kiwoom.api.base-url=http://localhost",
        "kiwoom.api.key=test-key",
        "kiwoom.api.secret=test-secret"
})
class KiwoomApplicationTest {

    @Test
    void contextLoads() {
    }
}
