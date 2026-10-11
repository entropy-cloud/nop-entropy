package io.nop.lint.core.cli;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.lint.core.NopLintException;
import io.nop.lint.core.engine.LanguageRegistry;
import io.nop.lint.core.engine.LintProfile;
import io.nop.lint.core.suppress.BaselineFile;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The baseline CLI lifecycle end-to-end (roadmap item 27, Minimum Rules
 * #22): {@code --write-baseline} generation, {@code --baseline} suppression
 * with new violations still reported and the exit code describing only the
 * residual, and {@code --baseline-check} stale enforcement with exit code 1
 * — the "基线只减不增" hard gate.
 */
public class TestCliBaseline {

    private static final String RULES_PREFIX = "/test/lint/cli-rules";

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

    private CheckOutcome run(String baselineFlag, String fileName, String content)
            throws IOException {
        Path file = dir.resolve(fileName);
        if (content != null) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content);
        }
        LanguageRegistry registry = LanguageRegistry.discoverDefaults();
        TargetScanner.ScanResult scan = TargetScanner.scan(List.of(dir.toString()), registry);
        CliOptions.BaselineOp op = switch (baselineFlag) {
            case "--baseline" -> CliOptions.BaselineOp.APPLY;
            case "--baseline-check" -> CliOptions.BaselineOp.CHECK;
            case "--write-baseline" -> CliOptions.BaselineOp.WRITE;
            default -> CliOptions.BaselineOp.NONE;
        };
        return new CheckRunner(registry, new RuleSetLoader(), RULES_PREFIX)
                .run(scan, LintProfile.STANDARD, CliOptions.FixMode.NONE, op,
                        dir.resolve("baseline.yml").toString());
    }

    @Test
    public void writeBaselineRecordsTheResidualAndExitsClean() throws IOException {
        CheckOutcome outcome = run("--write-baseline", "A.java",
                "class A { void m() { throw new RuntimeException(\"x\");"
                        + " System.out.println(\"y\"); } }\n");

        Path baseline = dir.resolve("baseline.yml");
        assertTrue(Files.exists(baseline), "the generation wrote the baseline file");
        BaselineFile loaded = BaselineFile.load(baseline);
        assertEquals(2, loaded.entries().size(),
                "the error and the warning violations are both recorded");
        assertTrue(outcome.staleBaselineEntries().isEmpty());
        assertEquals(0, outcome.summary().getBaselinedDiagnostics());
    }

    @Test
    public void applySuppressesMatchedAndReportsOnlyNew() throws IOException {
        run("--write-baseline", "A.java",
                "class A { void m() { throw new RuntimeException(\"x\");"
                        + " System.out.println(\"y\"); } }\n");
        // a NEW violation joins the two baselined ones
        CheckOutcome outcome = run("--baseline", "A.java",
                "class A { void m() { throw new RuntimeException(\"x\");"
                        + " System.out.println(\"y\"); System.out.println(\"z\"); } }\n");

        assertEquals(2, outcome.summary().getBaselinedDiagnostics(),
                "the recorded violations are suppressed");
        FileFindings findings = outcome.findings().get(0);
        assertEquals(1, findings.diagnostics().size(), "only the new violation is reported");
        assertEquals("demo/no-print", findings.diagnostics().get(0).ruleId());
    }

    @Test
    public void applyModeExitCodeReflectsOnlyTheResidual() throws IOException {
        run("--write-baseline", "A.java",
                "class A { void m() { throw new RuntimeException(\"x\"); } }\n");
        // the baselined violation is ERROR-severity: fully suppressed, the
        // exit-code pipeline must never see it
        CheckOutcome outcome = run("--baseline", "A.java",
                "class A { void m() { throw new RuntimeException(\"x\"); } }\n");

        assertEquals(0, outcome.findings().get(0).diagnostics().size());
        assertEquals(false, outcome.hasErrorDiagnostics(),
                "the baselined error diagnostic must not drive the exit code");
        assertTrue(outcome.staleBaselineEntries().isEmpty());
    }

    @Test
    public void checkModeReportsStaleWithExitCondition() throws IOException {
        run("--write-baseline", "A.java",
                "class A { void m() { throw new RuntimeException(\"x\");"
                        + " System.out.println(\"y\"); } }\n");
        // the content shrinks: the println violation is gone — its baseline
        // entry is stale ("基线只减不增")
        CheckOutcome outcome = run("--baseline-check", "A.java",
                "class A { void m() { throw new RuntimeException(\"x\"); } }\n");

        assertEquals(1, outcome.staleBaselineEntries().size());
        assertTrue(outcome.hasStaleBaselineEntries(),
                "the check mode exposes the stale condition for the exit code");
        assertEquals(1, outcome.summary().getBaselinedDiagnostics(),
                "the surviving violation is still suppressed in check mode");
    }

    @Test
    public void checkModeWithFullyUsedBaselineIsClean() throws IOException {
        run("--write-baseline", "A.java",
                "class A { void m() { throw new RuntimeException(\"x\"); } }\n");
        CheckOutcome outcome = run("--baseline-check", "A.java",
                "class A { void m() { throw new RuntimeException(\"x\"); } }\n");

        assertFalse(outcome.hasStaleBaselineEntries());
    }

    @Test
    public void missingBaselineFileForApplyFailsClosed() {
        LanguageRegistry registry = LanguageRegistry.discoverDefaults();
        TargetScanner.ScanResult scan = TargetScanner.scan(List.of(dir.toString()), registry);
        NopLintException ex = assertThrows(NopLintException.class,
                () -> new CheckRunner(registry, new RuleSetLoader(), RULES_PREFIX)
                        .run(scan, LintProfile.STANDARD, CliOptions.FixMode.NONE,
                                CliOptions.BaselineOp.APPLY,
                                dir.resolve("no-such-baseline.yml").toString()));
        assertTrue(ex.getMessage().contains("baseline"), ex.getMessage());
    }

    // ==================== flag parsing ====================

    @Test
    public void baselineFlagsParseIntoDistinctOps() {
        assertEquals(CliOptions.BaselineOp.APPLY,
                CliOptions.parse("check", "src", "--baseline", "b.yml").baselineOp());
        assertEquals("b.yml", CliOptions.parse("check", "src", "--baseline", "b.yml").baselineFile());
        assertEquals(CliOptions.BaselineOp.CHECK,
                CliOptions.parse("check", "src", "--baseline-check", "b.yml").baselineOp());
        assertEquals(CliOptions.BaselineOp.WRITE,
                CliOptions.parse("check", "src", "--write-baseline", "b.yml").baselineOp());
        assertEquals(CliOptions.BaselineOp.NONE, CliOptions.parse("check", "src").baselineOp());
    }

    @Test
    public void baselineFlagsAreMutuallyExclusive() {
        assertThrows(NopLintException.class,
                () -> CliOptions.parse("check", "src", "--baseline", "a.yml",
                        "--baseline-check", "b.yml"));
        assertThrows(NopLintException.class,
                () -> CliOptions.parse("check", "src", "--write-baseline", "a.yml",
                        "--baseline", "b.yml"));
    }

    @Test
    public void writeBaselineCannotCombineWithFixFlow() {
        NopLintException ex = assertThrows(NopLintException.class,
                () -> CliOptions.parse("check", "src", "--write-baseline", "b.yml", "--fix"));
        assertTrue(ex.getMessage().contains("--write-baseline"), ex.getMessage());
        // --baseline with the fix flow is legal (the decision predicate rides
        // every pass)
        assertEquals(CliOptions.BaselineOp.APPLY, CliOptions.parse("check", "src",
                "--baseline", "b.yml", "--fix").baselineOp());
    }

    @Test
    public void fullCliEntryRunsTheBaselineLifecycle() throws IOException {
        Files.writeString(dir.resolve("A.java"),
                "class A { void m() { throw new RuntimeException(\"x\"); } }\n");

        LanguageRegistry registry = LanguageRegistry.discoverDefaults();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream();
        int exit = NopLintCli.runFull(
                new String[]{"check", dir.toString(), "--write-baseline",
                        dir.resolve("baseline.yml").toString()},
                registry, RULES_PREFIX,
                new java.io.PrintStream(out, true, StandardCharsets.UTF_8),
                new java.io.PrintStream(err, true, StandardCharsets.UTF_8));

        assertEquals(NopLintCli.EXIT_OK, exit,
                "the generation run exits clean regardless of reported findings; stderr: " + err);
        assertTrue(Files.exists(dir.resolve("baseline.yml")));
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("demo/no-bare-throw"),
                "the report still renders the recorded diagnostics");
    }
}
