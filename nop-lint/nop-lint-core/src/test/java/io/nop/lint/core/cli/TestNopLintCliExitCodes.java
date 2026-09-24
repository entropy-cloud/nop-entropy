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
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link NopLintCli} exit-code mapping and report placement (Minimum Rules
 * #25): exit 0 on a clean tree, exit 1 when an error-severity diagnostic is
 * collected, exit 2 with a stderr message on every internal-error path —
 * never a silent swallow or a partial report presented as success. The full
 * chain runs in-process through {@link NopLintCli#run(String[],
 * LanguageRegistry, PrintStream, PrintStream)}.
 */
public class TestNopLintCliExitCodes {

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

    @Test
    public void cleanTreeExitsZeroAndReportsScannedFiles() throws IOException {
        Files.writeString(dir.resolve("clean.java"), "class Clean {\n}\n");
        Files.writeString(dir.resolve("notes.txt"), "skipped, counted");

        Run run = run("check", dir.toString());

        assertEquals(NopLintCli.EXIT_OK, run.exitCode());
        assertTrue(run.stderr().isEmpty(), run.stderr());
        assertTrue(run.stdout().contains("check complete: scanned=1 files, skipped=1 (txt=1)"),
                run.stdout());
        assertTrue(run.stdout().contains("(total=0)"), run.stdout());
    }

    @Test
    public void warningOnlyViolationsStillExitZero() throws IOException {
        Files.writeString(dir.resolve("warn.java"),
                "class Warn {\n    void x() {\n        System.out.println(\"w\");\n    }\n}\n");

        Run run = run("check", dir.toString());

        assertEquals(NopLintCli.EXIT_OK, run.exitCode(), run.stderr());
        assertTrue(run.stdout().contains("warning: demo/no-print:"), run.stdout());
        assertTrue(run.stdout().contains("diagnostics: error=0, warning=1"), run.stdout());
    }

    @Test
    public void errorSeverityDiagnosticExitsOneWithDiagnosticLine() throws IOException {
        Files.writeString(dir.resolve("bad.java"),
                "class Bad {\n    void x() {\n        throw new RuntimeException(\"boom\");\n    }\n}\n");

        Run run = run("check", dir.toString());

        assertEquals(NopLintCli.EXIT_VIOLATIONS, run.exitCode());
        assertTrue(run.stdout().contains("error: demo/no-bare-throw:"), run.stdout());
        assertTrue(run.stdout().contains("diagnostics: error=1"), run.stdout());
    }

    @Test
    public void fastProfileRunsThroughTheSameExitContract() throws IOException {
        Files.writeString(dir.resolve("clean.java"), "class Clean {\n}\n");

        Run run = run("check", "--profile", "fast", dir.toString());

        assertEquals(NopLintCli.EXIT_OK, run.exitCode(), run.stderr());
        assertTrue(run.stdout().contains("check complete"), run.stdout());
    }

    @Test
    public void unknownOptionExitsTwoWithStderrUsage() {
        // R1 3.1 behavior migration (roadmap item 39): --max-warnings was the
        // v1 unknown-option fixture and is now a supported flag — a still
        // unknown option carries the fail-closed assertion
        Run run = run("check", "--bogus", dir.toString());

        assertEquals(NopLintCli.EXIT_INTERNAL, run.exitCode());
        assertTrue(run.stderr().contains("nop-lint: error:"), run.stderr());
        assertTrue(run.stderr().contains("unknown option '--bogus'"), run.stderr());
    }

    @Test
    public void maxWarningsGateForcesExitOneBeyondTheLimit() throws IOException {
        Files.writeString(dir.resolve("warn.java"),
                "class Warn {\n    void x() {\n        System.out.println(\"w\");\n    }\n}\n");

        // one warning with the gate at zero: breached
        Run breached = run("check", "--max-warnings", "0", dir.toString());
        assertEquals(NopLintCli.EXIT_VIOLATIONS, breached.exitCode(), breached.stderr());

        // the same run with headroom: exit 0 (the error semantics untouched)
        Run within = run("check", "--max-warnings", "5", dir.toString());
        assertEquals(NopLintCli.EXIT_OK, within.exitCode(), within.stderr());
    }

    @Test
    public void nonexistentTargetExitsTwoWithStderrMessage() {
        Run run = run("check", dir.resolve("missing").toString());

        assertEquals(NopLintCli.EXIT_INTERNAL, run.exitCode());
        assertTrue(run.stderr().contains("target path does not exist"), run.stderr());
        assertTrue(run.stdout().isEmpty(), "no partial report may render as success: "
                + run.stdout());
    }

    @Test
    public void missingProductionRuleSetOnClasspathExitsTwoViaFullEntry() {
        // nop-lint-nop (the production rule library) is absent from this
        // module's test classpath: the full production entry point must stop
        // with the explicit deployment error, never an empty silent run
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = NopLintCli.run(new String[]{"check", dir.toString()},
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));

        assertEquals(NopLintCli.EXIT_INTERNAL, code);
        assertTrue(err.toString().contains("no lint rule files"), err.toString());
        assertTrue(err.toString().contains(RuleSetLoader.DEFAULT_RULES_PREFIX), err.toString());
    }
}
