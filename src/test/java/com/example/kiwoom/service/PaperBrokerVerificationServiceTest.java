package com.example.kiwoom.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PaperBrokerVerificationServiceTest {
    @Test
    void verifiesEveryLifecycleScenarioWithoutExternalOrders() {
        var report = new PaperBrokerVerificationService().verify();
        assertThat(report.passed()).isTrue();
        assertThat(report.trace()).anyMatch(value -> value.startsWith("RECOVERED"));
    }
}
