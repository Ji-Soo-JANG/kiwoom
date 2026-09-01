import java.util.Map;

public final class ChangedCoverageCheckerTest {
    private static final String XML =
            """
            <report><package name="com/example"><sourcefile name="Calc.java">
            <line nr="1" mi="0" ci="1" mb="0" cb="0"/>
            <line nr="2" mi="0" ci="1" mb="0" cb="2"/>
            <line nr="3" mi="2" ci="6" mb="1" cb="1"/>
            <line nr="4" mi="4" ci="4" mb="2" cb="1"/>
            </sourcefile></package></report>
            """;

    private static String diffAt(int line, String body) {
        return "diff --git a/src/main/java/com/example/Calc.java"
                + " b/src/main/java/com/example/Calc.java\n"
                + "+++ b/src/main/java/com/example/Calc.java\n"
                + "@@ -1 +"
                + line
                + " @@\n"
                + body;
    }

    private static Map<String, String> src(String text) {
        return Map.of("src/main/java/com/example/Calc.java", text);
    }

    private static void eq(Object a, Object b) {
        if (!a.equals(b)) throw new AssertionError(a + " != " + b);
    }

    private static void ok(boolean v) {
        if (!v) throw new AssertionError();
    }

    public static void main(String[] args) throws Exception {
        eq(ChangedCoverageChecker.parseDiff(diffAt(1, "+x\n")).get(0).lines(), java.util.Set.of(1));
        var source = src("x\ny\nz\nw\n");
        var simple = ChangedCoverageChecker.check(diffAt(1, "+x\n"), XML, source, .80, .70);
        eq(simple.status(), "PASS");
        eq(simple.changedLines(), 1);
        eq(simple.branchTotal(), 0);
        var full = ChangedCoverageChecker.check(diffAt(2, "+y\n"), XML, source, .80, .70);
        eq(full.branchTotal(), 2);
        eq(full.branchCovered(), 2);
        var partial = ChangedCoverageChecker.check(diffAt(3, "+z\n"), XML, source, .80, .70);
        eq(partial.branchTotal(), 2);
        eq(partial.branchCovered(), 1);
        eq(partial.status(), "COVERAGE_FAIL");
        var comment =
                ChangedCoverageChecker.check(
                        diffAt(1, "+// comment\n"), XML, src("// comment\ny\nz\nw\n"), .80, .70);
        eq(comment.status(), "NOT_APPLICABLE");
        eq(comment.changedLines(), 0);
        ok(Double.isNaN(comment.lineRatio()));
        var importOnly =
                ChangedCoverageChecker.check(
                        diffAt(1, "+import x;\n"), XML, src("import x;\ny\nz\nw\n"), .80, .70);
        eq(importOnly.status(), "NOT_APPLICABLE");
        var unmapped =
                ChangedCoverageChecker.check(
                        diffAt(5, "+return 42;\n"), XML, src("x\ny\nz\nw\nreturn 42;\n"), .80, .70);
        eq(unmapped.status(), "UNMAPPED_PRODUCTION_CODE");
        var deleted =
                ChangedCoverageChecker.check(
                        "diff --git a/src/main/java/com/example/Calc.java"
                                + " b/src/main/java/com/example/Calc.java\n"
                                + "+++ b/src/main/java/com/example/Calc.java\n"
                                + "@@ -1 +0,0 @@\n"
                                + "-x\n",
                        XML,
                        source,
                        .80,
                        .70);
        eq(deleted.status(), "NOT_APPLICABLE");
        var annotation =
                ChangedCoverageChecker.check(
                        diffAt(5, "+@Deprecated\n"),
                        XML,
                        src("x\ny\nz\nw\n@Deprecated\n"),
                        .80,
                        .70);
        eq(annotation.status(), "NOT_APPLICABLE");
        var record =
                ChangedCoverageChecker.check(
                        "diff --git a/src/main/java/com/example/Record.java"
                                + " b/src/main/java/com/example/Record.java\n"
                                + "+++ b/src/main/java/com/example/Record.java\n"
                                + "@@ -1,0 +1,3 @@\n"
                                + "+record Record(\n"
                                + "+  String first,\n"
                                + "+  int second) {}\n",
                        "<report><package name=\"com/example\"><sourcefile"
                                + " name=\"Record.java\"><line nr=\"1\" mi=\"0\""
                                + " ci=\"1\"/></sourcefile></package></report>",
                        Map.of(
                                "src/main/java/com/example/Record.java",
                                "record Record(\n  String first,\n  int second) {}\n"),
                        .80,
                        .70);
        eq(record.status(), "PASS");
        var executableContinuation =
                ChangedCoverageChecker.check(
                        "diff --git a/src/main/java/com/example/Call.java"
                                + " b/src/main/java/com/example/Call.java\n"
                                + "+++ b/src/main/java/com/example/Call.java\n"
                                + "@@ -1,0 +1,3 @@\n"
                                + "+void call() {\n"
                                + "+  doWork();\n"
                                + "+}\n",
                        "<report><package name=\"com/example\"><sourcefile name=\"Call.java\"><line"
                                + " nr=\"1\" mi=\"0\" ci=\"1\"/></sourcefile></package></report>",
                        Map.of(
                                "src/main/java/com/example/Call.java",
                                "void call() {\n  doWork();\n}\n"),
                        .80,
                        .70);
        eq(executableContinuation.status(), "UNMAPPED_PRODUCTION_CODE");
        var declarations =
                ChangedCoverageChecker.check(
                        "diff --git a/src/main/java/com/example/Declarations.java"
                                + " b/src/main/java/com/example/Declarations.java\n"
                                + "+++ b/src/main/java/com/example/Declarations.java\n"
                                + "@@ -1,0 +1,14 @@\n"
                                + "+class Declarations {\n"
                                + "+  @Deprecated(\n"
                                + "+      since = \"1\"\n"
                                + "+  )\n"
                                + "+  public <T> T method(\n"
                                + "+      T value\n"
                                + "+  ) {\n"
                                + "+    return value;\n"
                                + "+  }\n"
                                + "+}\n",
                        "<report><package name=\"com/example\"><sourcefile"
                                + " name=\"Declarations.java\"/></package></report>",
                        Map.of(
                                "src/main/java/com/example/Declarations.java",
                                "class Declarations {\n"
                                        + "  @Deprecated(\n"
                                        + "      since = \"1\"\n"
                                        + "  )\n"
                                        + "  public <T> T method(\n"
                                        + "      T value\n"
                                        + "  ) {\n"
                                        + "    return value;\n"
                                        + "  }\n"
                                        + "}\n"),
                        .80,
                        .70);
        eq(declarations.status(), "UNMAPPED_PRODUCTION_CODE");
        var multilineField =
                ChangedCoverageChecker.check(
                        "diff --git a/src/main/java/com/example/Field.java"
                                + " b/src/main/java/com/example/Field.java\n"
                                + "+++ b/src/main/java/com/example/Field.java\n"
                                + "@@ -1,0 +1,4 @@\n"
                                + "+class Field {\n"
                                + "+  private final java.util.function.Supplier<String>\n"
                                + "+      value;\n"
                                + "+}\n",
                        "<report><package name=\"com/example\"><sourcefile"
                                + " name=\"Field.java\"><line nr=\"1\" mi=\"0\" ci=\"1\"/>"
                                + "</sourcefile></package></report>",
                        Map.of(
                                "src/main/java/com/example/Field.java",
                                "class Field {\n"
                                        + "  private final java.util.function.Supplier<String>\n"
                                        + "      value;\n"
                                        + "}\n"),
                        .80,
                        .70);
        eq(multilineField.status(), "PASS");
        var lambdaChain =
                ChangedCoverageChecker.check(
                        "diff --git a/src/main/java/com/example/Chain.java"
                                + " b/src/main/java/com/example/Chain.java\n"
                                + "+++ b/src/main/java/com/example/Chain.java\n"
                                + "@@ -1,0 +1,5 @@\n"
                                + "+return values.stream()\n"
                                + "+    .filter(value ->\n"
                                + "+        value > 0)\n"
                                + "+    .map(value -> value + 1)\n"
                                + "+    .toList();\n",
                        "<report><package name=\"com/example\"><sourcefile"
                                + " name=\"Chain.java\"><line nr=\"1\" mi=\"0\""
                                + " ci=\"1\"/></sourcefile></package></report>",
                        Map.of(
                                "src/main/java/com/example/Chain.java",
                                "return values.stream()\n"
                                        + "    .filter(value ->\n"
                                        + "        value > 0)\n"
                                        + "    .map(value -> value + 1)\n"
                                        + "    .toList();\n"),
                        .80,
                        .70);
        eq(lambdaChain.status(), "PASS");
        var switchLabels =
                ChangedCoverageChecker.check(
                        "diff --git a/src/main/java/com/example/Switch.java"
                                + " b/src/main/java/com/example/Switch.java\n"
                                + "+++ b/src/main/java/com/example/Switch.java\n"
                                + "@@ -1,0 +1,5 @@\n"
                                + "+switch (value) {\n"
                                + "+  case 1 ->\n"
                                + "+      result = 1;\n"
                                + "+  default -> result = 0;\n"
                                + "}\n",
                        "<report><package name=\"com/example\"><sourcefile"
                                + " name=\"Switch.java\"><line nr=\"1\" mi=\"0\""
                                + " ci=\"1\" mb=\"0\" cb=\"2\"/><line nr=\"3\""
                                + " mi=\"0\" ci=\"1\"/><line nr=\"4\" mi=\"0\""
                                + " ci=\"1\"/></sourcefile></package></report>",
                        Map.of(
                                "src/main/java/com/example/Switch.java",
                                "switch (value) {\n"
                                        + "  case 1 ->\n"
                                        + "      result = 1;\n"
                                        + "  default -> result = 0;\n"
                                        + "}\n"),
                        .80,
                        .70);
        eq(switchLabels.status(), "PASS");
        var rename =
                "diff --git a/src/main/java/com/example/Old.java"
                        + " b/src/main/java/com/example/New.java\n"
                        + "rename from Old.java\n"
                        + "rename to New.java\n"
                        + "+++ b/src/main/java/com/example/New.java\n"
                        + "@@ -1 +1 @@\n"
                        + "+return 1;\n";
        eq(
                ChangedCoverageChecker.check(
                                rename,
                                XML,
                                Map.of("src/main/java/com/example/New.java", "return 1;\n"),
                                .80,
                                .70)
                        .status(),
                "UNMAPPED_PRODUCTION_CODE");
        var exactLine =
                ChangedCoverageChecker.check(
                        "diff --git a/src/main/java/com/example/Calc.java"
                                + " b/src/main/java/com/example/Calc.java\n"
                                + "+++ b/src/main/java/com/example/Calc.java\n"
                                + "@@ -1,0 +1,5 @@\n"
                                + "+x\n"
                                + "+y\n"
                                + "+z\n"
                                + "+w\n"
                                + "+q\n",
                        "<report><package name=\"com/example\"><sourcefile name=\"Calc.java\"><line"
                            + " nr=\"1\" mi=\"0\" ci=\"1\" mb=\"0\" cb=\"14\"/><line nr=\"2\""
                            + " mi=\"0\" ci=\"1\"/><line nr=\"3\" mi=\"0\" ci=\"1\"/><line nr=\"4\""
                            + " mi=\"0\" ci=\"1\"/><line nr=\"5\" mi=\"1\""
                            + " ci=\"0\"/></sourcefile></package></report>",
                        src("x\ny\nz\nw\nq\n"),
                        .80,
                        .70);
        eq(exactLine.status(), "PASS");
        eq(exactLine.coveredLines(), 4);
        eq(
                ChangedCoverageChecker.check(
                                "diff --git a/src/main/java/com/example/Calc.java"
                                        + " b/src/main/java/com/example/Calc.java\n"
                                        + "+++ b/src/main/java/com/example/Calc.java\n"
                                        + "@@ -1,0 +1,5 @@\n"
                                        + "+x\n"
                                        + "+y\n"
                                        + "+z\n"
                                        + "+w\n"
                                        + "+q\n",
                                "<report><package name=\"com/example\"><sourcefile"
                                    + " name=\"Calc.java\"><line nr=\"1\" mi=\"0\" ci=\"1\"/><line"
                                    + " nr=\"2\" mi=\"0\" ci=\"1\"/><line nr=\"3\" mi=\"0\""
                                    + " ci=\"1\"/><line nr=\"4\" mi=\"0\" ci=\"1\"/><line nr=\"5\""
                                    + " mi=\"1\" ci=\"0\"/></sourcefile></package></report>",
                                src("x\ny\nz\nw\nq\n"),
                                .8001,
                                .70)
                        .status(),
                "COVERAGE_FAIL");
        eq(ChangedCoverageChecker.check(diffAt(3, "+z\n"), XML, source, .80, .50).status(), "PASS");
        var newClass =
                ChangedCoverageChecker.check(
                        "diff --git a/src/main/java/com/example/NewClass.java"
                                + " b/src/main/java/com/example/NewClass.java\n"
                                + "new file mode 100644\n"
                                + "+++ b/src/main/java/com/example/NewClass.java\n"
                                + "@@ -0,0 +1,2 @@\n"
                                + "+class NewClass {\n"
                                + "+  int value() { return 1; }\n"
                                + "+}\n",
                        "<report><package name=\"com/example\"><sourcefile"
                                + " name=\"NewClass.java\"><line nr=\"2\" mi=\"1\""
                                + " ci=\"0\"/></sourcefile></package></report>",
                        Map.of(
                                "src/main/java/com/example/NewClass.java",
                                "class NewClass {\n  int value() { return 1; }\n}\n"),
                        .80,
                        .70);
        eq(newClass.status(), "COVERAGE_FAIL");
        var exactBranchXml =
                "<report><package name=\"com/example\"><sourcefile name=\"Calc.java\"><line"
                        + " nr=\"1\" mi=\"0\" ci=\"1\" mb=\"3\""
                        + " cb=\"7\"/></sourcefile></package></report>";
        eq(
                ChangedCoverageChecker.check(
                                diffAt(1, "+x\n"), exactBranchXml, src("x\n"), .80, .70)
                        .status(),
                "PASS");
        eq(
                ChangedCoverageChecker.check(
                                diffAt(1, "+x\n"), exactBranchXml, src("x\n"), .80, .7001)
                        .status(),
                "COVERAGE_FAIL");
        var current = ChangedCoverageChecker.check(diffAt(1, "+x\n"), XML, source, .80, .70);
        eq(
                ChangedCoverageChecker.withGlobal(
                                current,
                                "<report><counter type=\"LINE\" missed=\"2\""
                                        + " covered=\"8\"/><counter type=\"BRANCH\" missed=\"2\""
                                        + " covered=\"8\"/></report>",
                                "<report><counter type=\"LINE\" missed=\"3\""
                                        + " covered=\"7\"/><counter type=\"BRANCH\" missed=\"3\""
                                        + " covered=\"7\"/></report>")
                        .status(),
                "GLOBAL_REGRESSION");
        boolean malformed = false;
        try {
            ChangedCoverageChecker.check(diffAt(1, "+x\n"), "<report>", source, .80, .70);
        } catch (Exception e) {
            malformed = true;
        }
        ok(malformed);
        System.out.println("CHECKER_TESTS_PASS=16");
    }
}
