package io.nop.lint.core.cli;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.lint.core.engine.LanguageRegistry;
import io.nop.lint.core.testing.JavaBindingTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The CLI-completion surface (roadmap item 39, design 03 §2.4): the four
 * machine-readable {@code --format} faces render the real diagnostic stream
 * (ruleId/severity/message/line each verifiable), the {@code --rules}
 * whitelist narrows fail-closed, and the {@code match}/{@code test}
 * subcommands run through the real pattern compiler and RuleTester runner.
 * Everything executes in-process through {@link NopLintCli#runFull}.
 */
public class TestNopLintCliCompletion {

    private static final String VALID_PREFIX = "/test/lint/cli-rules";

    @TempDir
    Path dir;

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private record Run(int exitCode, String stdout, String stderr) {
    }

    private Run run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = NopLintCli.runFull(args, JavaBindingTestSupport.registryWithJava(),
                VALID_PREFIX,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Run(code, out.toString(StandardCharsets.UTF_8),
                err.toString(StandardCharsets.UTF_8));
    }

    private void writeViolatingPair() throws Exception {
        Files.writeString(dir.resolve("warn.java"),
                "class Warn {\n    void x() {\n        System.out.println(\"w\");\n    }\n}\n");
        Files.writeString(dir.resolve("bad.java"),
                "class Bad {\n    void x() {\n        throw new RuntimeException(\"boom\");\n    }\n}\n");
    }

    @Test
    public void jsonFormatRendersDiagnosticsAndSummary() throws Exception {
        writeViolatingPair();

        Run run = run("check", "--format", "json", dir.toString());

        assertEquals(NopLintCli.EXIT_VIOLATIONS, run.exitCode(), run.stderr());
        assertTrue(run.stdout().contains("\"ruleId\": \"demo/no-print\""), run.stdout());
        assertTrue(run.stdout().contains("\"severity\": \"error\""), run.stdout());
        assertTrue(run.stdout().contains("\"message\": \"Do not throw bare RuntimeException"),
                run.stdout());
        assertTrue(run.stdout().contains("\"line\": 3"), run.stdout());
        assertTrue(run.stdout().contains("\"path\": \"" + dir + "/bad.java\""), run.stdout());
        assertTrue(run.stdout().contains("\"total\": 2"), run.stdout());
        assertTrue(!run.stdout().contains("check complete"),
                "the machine format replaces the console summary: " + run.stdout());
    }

    @Test
    public void sarifFormatIsMinimalCompliantWithSeverityMapping() throws Exception {
        writeViolatingPair();

        Run run = run("check", "--format", "sarif", dir.toString());

        assertEquals(NopLintCli.EXIT_VIOLATIONS, run.exitCode(), run.stderr());
        assertTrue(run.stdout().contains("\"version\": \"2.1.0\""), run.stdout());
        assertTrue(run.stdout().contains("\"ruleId\": \"demo/no-bare-throw\""), run.stdout());
        assertTrue(run.stdout().contains("\"level\": \"error\""), run.stdout());
        assertTrue(run.stdout().contains("\"level\": \"warning\""), run.stdout());
        assertTrue(run.stdout().contains("\"startLine\": 3"), run.stdout());
        assertTrue(run.stdout().contains("\"text\": \"Do not use System.out.println"), run.stdout());
    }

    @Test
    public void checkstyleXmlFormatGroupsByFile() throws Exception {
        writeViolatingPair();

        Run run = run("check", "--format", "checkstyle-xml", dir.toString());

        assertEquals(NopLintCli.EXIT_VIOLATIONS, run.exitCode(), run.stderr());
        assertTrue(run.stdout().startsWith("<?xml version=\"1.0\" encoding=\"utf-8\"?>"),
                run.stdout());
        assertTrue(run.stdout().contains("<file name=\"" + dir + "/warn.java\">"), run.stdout());
        assertTrue(run.stdout().contains("severity=\"error\""), run.stdout());
        assertTrue(run.stdout().contains("source=\"demo/no-bare-throw\""), run.stdout());
    }

    @Test
    public void junitXmlFormatNamesRulesAsTestcases() throws Exception {
        writeViolatingPair();

        Run run = run("check", "--format", "junit-xml", dir.toString());

        assertEquals(NopLintCli.EXIT_VIOLATIONS, run.exitCode(), run.stderr());
        assertTrue(run.stdout().contains("<testsuites>"), run.stdout());
        assertTrue(run.stdout().contains("tests=\"1\" failures=\"1\""), run.stdout());
        assertTrue(run.stdout().contains("<testcase name=\"demo/no-print\""), run.stdout());
        assertTrue(run.stdout().contains("<failure message=\"Do not use System.out.println"),
                run.stdout());
    }

    @Test
    public void unknownFormatExitsTwo() throws Exception {
        Files.writeString(dir.resolve("clean.java"), "class Clean {\n}\n");

        Run run = run("check", "--format", "xml", dir.toString());

        assertEquals(NopLintCli.EXIT_INTERNAL, run.exitCode());
        assertTrue(run.stderr().contains("unknown format 'xml'"), run.stderr());
    }

    @Test
    public void negativeMaxWarningsExitsTwo() throws Exception {
        Files.writeString(dir.resolve("clean.java"), "class Clean {\n}\n");

        Run run = run("check", "--max-warnings", "-1", dir.toString());

        assertEquals(NopLintCli.EXIT_INTERNAL, run.exitCode());
        assertTrue(run.stderr().contains("--max-warnings requires a non-negative integer"),
                run.stderr());
    }

    @Test
    public void rulesWhitelistNarrowsTheRun() throws Exception {
        writeViolatingPair();

        // only the throw rule survives the narrowing: the print warning
        // disappears, the error still drives exit 1
        Run run = run("check", "--rules", "demo/no-bare-throw", dir.toString());

        assertEquals(NopLintCli.EXIT_VIOLATIONS, run.exitCode(), run.stderr());
        assertTrue(!run.stdout().contains("demo/no-print"), run.stdout());
        assertTrue(run.stdout().contains("demo/no-bare-throw"), run.stdout());
    }

    @Test
    public void rulesWhitelistUnknownIdFailsClosed() throws Exception {
        Files.writeString(dir.resolve("clean.java"), "class Clean {\n}\n");

        Run run = run("check", "--rules", "demo/no-such-rule", dir.toString());

        assertEquals(NopLintCli.EXIT_INTERNAL, run.exitCode());
        assertTrue(run.stderr().contains("demo/no-such-rule"), run.stderr());
    }

    @Test
    public void matchPrintsRealHitsWithLineAndColumn() throws Exception {
        Path file = dir.resolve("sample.java");
        Files.writeString(file,
                "class Sample {\n    void x() {\n        System.out.println(\"a\");\n"
                        + "        System.out.println(\"b\");\n    }\n}\n");

        Run run = run("match", "System.out.println($$$ARGS)", file.toString());

        assertEquals(NopLintCli.EXIT_OK, run.exitCode(), run.stderr());
        assertTrue(run.stdout().contains("sample.java:3:9: System.out.println(\"a\")"), run.stdout());
        assertTrue(run.stdout().contains("sample.java:4:9: System.out.println(\"b\")"), run.stdout());
        assertTrue(run.stdout().endsWith("2 match(es)\n"), run.stdout());
    }

    @Test
    public void matchWithBrokenPatternExitsTwo() throws Exception {
        Path file = dir.resolve("sample.java");
        Files.writeString(file, "class Sample {\n}\n");

        Run run = run("match", "System.out.println(", file.toString());

        assertEquals(NopLintCli.EXIT_INTERNAL, run.exitCode());
        assertTrue(run.stderr().contains("nop-lint: error:"), run.stderr());
    }

    @Test
    public void testSubcommandRunsRealSuitesGreen() {
        Run run = run("test", "/test/lint/cli-test-suites-green");

        assertEquals(NopLintCli.EXIT_OK, run.exitCode(), run.stderr());
        assertTrue(run.stdout().contains(": green"), run.stdout());
        assertTrue(run.stdout().endsWith("1 suite(s), 0 red\n"), run.stdout());
    }

    @Test
    public void testSubcommandShowsRedSuiteAsExitOne() {
        Run run = run("test", "/test/lint/cli-test-suites-mixed");

        assertEquals(NopLintCli.EXIT_VIOLATIONS, run.exitCode(), run.stderr());
        assertTrue(run.stdout().contains(": RED"), run.stdout());
        assertTrue(run.stdout().endsWith("1 suite(s), 1 red\n"), run.stdout());
    }

    @Test
    public void testSubcommandWithMissingPathExitsTwo() {
        Run run = run("test", "/test/lint/no-such-suites");

        assertEquals(NopLintCli.EXIT_INTERNAL, run.exitCode());
        assertTrue(run.stderr().contains("nop-lint: error:"), run.stderr());
    }
}
