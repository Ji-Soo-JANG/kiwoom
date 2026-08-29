package com.example.kiwoom.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Guards the approved STR-P06 boundary without coupling the test to a particular Spring design.
 * Class-file inspection catches constructor, field, annotation and method references as well as
 * forbidden SQL table literals.
 */
class ResearchIsolationArchitectureTest {
    private static final String RESEARCH_PACKAGE = "com/example/kiwoom/research";

    private static final List<String> FORBIDDEN_CLASS_REFERENCES =
            List.of(
                    "com/example/kiwoom/service/broker/",
                    "com/example/kiwoom/service/PaperOrderService",
                    "com/example/kiwoom/service/LimitedTradingService",
                    "com/example/kiwoom/service/AutoTradingControlService",
                    "com/example/kiwoom/service/PaperRiskService",
                    "com/example/kiwoom/service/StrategyScanService",
                    "com/example/kiwoom/service/strategy/",
                    "com/example/kiwoom/repository/PaperTradingRepository",
                    "com/example/kiwoom/repository/LimitedTradingRepository",
                    "com/example/kiwoom/repository/AutoTradingControlRepository");

    private static final List<String> FORBIDDEN_TRADING_TABLES =
            List.of(
                    "trading_order",
                    "trading_fill",
                    "paper_account",
                    "paper_position",
                    "limited_trade_candidate",
                    "auto_trading_control");

    @Test
    void researchPackageHasNoOrderOrAutomationDependency() throws Exception {
        List<Path> classes = researchClassFiles();

        assertThat(classes).as("PH1-01 연구 패키지가 존재해야 안전 경계를 검사할 수 있다").isNotEmpty();

        List<String> violations = new ArrayList<>();
        for (Path classFile : classes) {
            String constantPool =
                    new String(Files.readAllBytes(classFile), StandardCharsets.ISO_8859_1);
            FORBIDDEN_CLASS_REFERENCES.stream()
                    .filter(constantPool::contains)
                    .map(reference -> display(classFile) + " -> " + reference)
                    .forEach(violations::add);
        }

        assertThat(violations).as("STR-P06 연구 코드는 주문·자동매매 서비스와 브로커를 참조할 수 없다").isEmpty();
    }

    @Test
    void researchPackageCannotWriteTradingTables() throws Exception {
        List<String> violations = new ArrayList<>();
        for (Path classFile : researchClassFiles()) {
            String constantPool =
                    new String(Files.readAllBytes(classFile), StandardCharsets.ISO_8859_1)
                            .toLowerCase(java.util.Locale.ROOT);
            FORBIDDEN_TRADING_TABLES.stream()
                    .filter(constantPool::contains)
                    .map(table -> display(classFile) + " -> " + table)
                    .forEach(violations::add);
        }

        assertThat(violations).as("STR-P06 연구 코드는 주문·계좌·자동매매 테이블 SQL을 포함할 수 없다").isEmpty();
    }

    private List<Path> researchClassFiles() throws IOException {
        Path packageRoot = Path.of("target", "classes").resolve(RESEARCH_PACKAGE);
        assertThat(packageRoot).as("컴파일된 STR-P06 운영 연구 패키지").isDirectory();
        try (var paths = Files.walk(packageRoot)) {
            return paths.filter(path -> path.toString().endsWith(".class")).sorted().toList();
        }
    }

    private String display(Path classFile) {
        return classFile.getFileName().toString();
    }
}
