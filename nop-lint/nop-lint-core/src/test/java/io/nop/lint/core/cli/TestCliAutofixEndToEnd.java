package io.nop.lint.core.cli;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.lint.core.NopLintException;
import io.nop.lint.core.engine.LanguageRegistry;
import io.nop.lint.core.engine.LintProfile;
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
 * The autofix end-to-end proof (roadmap item 25, Minimum Rules #22/#23):
 * real fixture rules through the classpath VFS, real files in a temp
 * directory, and the full chain from the CLI surface (or the CheckRunner)
 * to the bytes on disk. The three plan scenarios — conflict merge,
 * multipass convergence, syntax-break rollback — each carry a dedicated
 * e2e assertion, alongside the write-back byte check, the dry-run diff,
 * and the suggestion semantics.
 */
public class TestCliAutofixEndToEnd {

    private static final String RULES_PREFIX = "/test/lint/cli-rules-fix";

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

    private CheckOutcome runCheck(String fileName, String content, CliOptions.FixMode fixMode)
            throws IOException {
        Path file = dir.resolve(fileName);
        Files.writeString(file, content);

        LanguageRegistry registry = LanguageRegistry.discoverDefaults();
        TargetScanner.ScanResult scan = TargetScanner.scan(List.of(dir.toString()), registry);
        return new CheckRunner(registry, new RuleSetLoader(), RULES_PREFIX)
                .run(scan, LintProfile.STANDARD, fixMode);
    }

    // ==================== apply ====================

    @Test
    public void fixWritesTheRenderedTemplateBackToTheFile() throws IOException {
        CheckOutcome outcome = runCheck("Basic.java",
                "class Basic { void m() { System.out.println(\"x\"); } }\n", CliOptions.FixMode.APPLY);

        assertEquals("class Basic { void m() { log(\"x\"); } }\n",
                Files.readString(dir.resolve("Basic.java")),
                "the template rewrite reached the bytes on disk");
        assertEquals(1, outcome.summary().getFixesApplied());
        assertEquals(0, outcome.summary().getFixConflictsSkipped());
        assertEquals(0, outcome.summary().getFixRollbacks());

        // the report describes the on-disk content: the rewritten log call is
        // what a re-run sees, and fix-chain-log now flags it (fix output
        // re-enters the normal pipeline)
        FileFindings basic = outcome.findings().get(0);
        assertEquals(1, basic.diagnostics().size());
        assertEquals("demo/fix-chain-log", basic.diagnostics().get(0).ruleId());
    }

    @Test
    public void conflictingRulesApplyOnlyTheDeclarationOrderWinner() throws IOException {
        CheckOutcome outcome = runCheck("Trace.java",
                "class Trace { void m() { trace.write(\"z\"); } }\n", CliOptions.FixMode.APPLY);

        assertEquals("class Trace { void m() { logA(\"z\"); } }\n",
                Files.readString(dir.resolve("Trace.java")),
                "conflict-a is declared first and wins the overlapping range");
        assertEquals(1, outcome.summary().getFixesApplied());
        assertEquals(1, outcome.summary().getFixConflictsSkipped(),
                "conflict-b's candidate is counted, never silently dropped");
    }

    @Test
    public void multipassConvergesAcrossChainedRules() throws IOException {
        CheckOutcome outcome = runCheck("Mixed.java",
                "class Mixed { void m() { System.out.println(\"a\"); System.out.println(\"b\");"
                        + " log(\"c\"); } }\n",
                CliOptions.FixMode.APPLY);

        assertEquals("class Mixed { void m() { logger.info(\"a\"); logger.info(\"b\");"
                        + " logger.info(\"c\"); } }\n",
                Files.readString(dir.resolve("Mixed.java")),
                "pass 1 rewrites the println pair, pass 2 rewrites all three log calls");
        assertEquals(5, outcome.summary().getFixesApplied(),
                "pass 1 applies 3 rewrites, pass 2 applies 2 more");
        assertEquals(0, outcome.summary().getFixFilesNonConvergent());
        assertEquals(0, outcome.findings().get(0).diagnostics().size(),
                "the final content is clean under the whole rule set");
    }

    @Test
    public void syntaxBreakRollsBackAndCountsTheRollback() throws IOException {
        String source = "class Boom { void m() { throw new RuntimeException(\"boom\"); } }\n";
        CheckOutcome outcome = runCheck("Boom.java", source, CliOptions.FixMode.APPLY);

        assertEquals(source, Files.readString(dir.resolve("Boom.java")),
                "the broken rewrite was rolled back to the previous content");
        assertEquals(1, outcome.summary().getFixRollbacks());
        assertEquals(0, outcome.summary().getFixesApplied(),
                "the rolled-back rewrite is not counted as applied");
        // the report describes the restored content — the rule still fires
        FileFindings boom = outcome.findings().get(0);
        assertEquals(1, boom.diagnostics().size());
        assertEquals("demo/fix-bad-rollback", boom.diagnostics().get(0).ruleId());
    }

    @Test
    public void fileWithoutFixesIsReportedButUntouched() throws IOException {
        String source = "class Loop { void m() { cleanup(); } }\n";
        CheckOutcome outcome = runCheck("Loop.java", source, CliOptions.FixMode.APPLY);

        assertEquals(source, Files.readString(dir.resolve("Loop.java")));
        assertEquals(0, outcome.summary().getFixesApplied());
        assertEquals(1, outcome.findings().get(0).diagnostics().size(),
                "the no-fix rule's diagnostic is still reported");
        assertEquals("demo/fix-nofix-cleanup", outcome.findings().get(0).diagnostics().get(0).ruleId());
    }

    // ==================== dry-run ====================

    @Test
    public void dryRunPrintsTheDiffAndLeavesTheFileUntouched() throws IOException {
        String source = "class Basic { void m() { System.out.println(\"x\"); } }\n";
        CheckOutcome outcome = runCheck("Basic.java", source, CliOptions.FixMode.DRY_RUN);

        assertEquals(source, Files.readString(dir.resolve("Basic.java")),
                "the dry-run never writes");
        assertEquals(1, outcome.diffs().size());
        FileDiff diff = outcome.diffs().get(0);
        assertTrue(diff.displayPath().endsWith("Basic.java"));
        // the single-line source yields a single-line replacement hunk
        assertTrue(diff.unifiedDiff().contains(
                "-class Basic { void m() { System.out.println(\"x\"); } }"), diff.unifiedDiff());
        assertTrue(diff.unifiedDiff().contains(
                "+class Basic { void m() { log(\"x\"); } }"), diff.unifiedDiff());
        assertEquals(1, outcome.summary().getFixesApplied(),
                "the counters describe the proposed rewrite");
        // the report describes the untouched content: the original diagnostic
        assertTrue(outcome.findings().get(0).diagnostics().get(0).ruleId()
                .endsWith("fix-no-print"));
    }

    @Test
    public void dryRunOfAnUnchangedFileProducesNoDiffEntry() throws IOException {
        CheckOutcome outcome = runCheck("Loop.java", "class Loop { void m() { cleanup(); } }\n",
                CliOptions.FixMode.DRY_RUN);

        assertEquals(0, outcome.diffs().size(), "no proposal means no diff block");
        assertEquals(0, outcome.summary().getFixesApplied());
    }

    // ==================== suggestions ====================

    @Test
    public void suggestOnlyFixIsListedButNeverApplied() throws IOException {
        String source = "class Err { void m() { System.err.println(\"e\"); } }\n";
        CheckOutcome outcome = runCheck("Err.java", source, CliOptions.FixMode.APPLY);

        assertEquals(source, Files.readString(dir.resolve("Err.java")),
                "a suggestion-only fix never writes");
        assertEquals(0, outcome.summary().getFixesApplied());
        assertEquals(0, outcome.diffs().size());
        FileFindings err = outcome.findings().get(0);
        assertEquals(1, err.diagnostics().size());
        assertEquals("demo/fix-suggest-print", err.diagnostics().get(0).ruleId());
        assertTrue(outcome.suggestionDescriptions().containsKey("demo/fix-suggest-print"));
        assertEquals("Replace with the error log call",
                outcome.suggestionDescriptions().get("demo/fix-suggest-print"));
    }

    // ==================== CLI surface ====================

    @Test
    public void fullCliEntryAppliesFixesAndReturnsCleanExit() throws IOException {
        Files.writeString(dir.resolve("Basic.java"),
                "class Basic { void m() { System.out.println(\"x\"); } }\n");

        LanguageRegistry registry = LanguageRegistry.discoverDefaults();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream();
        int exit = NopLintCli.runFull(
                new String[]{"check", dir.toString(), "--fix"}, registry, RULES_PREFIX,
                new java.io.PrintStream(out, true, StandardCharsets.UTF_8),
                new java.io.PrintStream(err, true, StandardCharsets.UTF_8));

        assertEquals(NopLintCli.EXIT_OK, exit,
                "residual diagnostics are warnings only; stderr: " + err);
        assertEquals("class Basic { void m() { log(\"x\"); } }\n",
                Files.readString(dir.resolve("Basic.java")));
        String report = out.toString(StandardCharsets.UTF_8);
        assertTrue(report.contains("fix: applied="), report);
    }

    @Test
    public void fixFlagsAreMutuallyExclusive() {
        NopLintException ex = assertThrows(NopLintException.class,
                () -> CliOptions.parse("check", "src", "--fix", "--fix-dry-run"));
        assertTrue(ex.getMessage().contains("mutually exclusive"), ex.getMessage());
    }

    @Test
    public void fixFlagsParseIntoDistinctModes() {
        assertEquals(CliOptions.FixMode.APPLY,
                CliOptions.parse("check", "src", "--fix").fixMode());
        assertEquals(CliOptions.FixMode.DRY_RUN,
                CliOptions.parse("check", "src", "--fix-dry-run").fixMode());
        assertEquals(CliOptions.FixMode.NONE,
                CliOptions.parse("check", "src").fixMode());
        assertFalse(CliOptions.parse("check", "src", "--profile", "fast").fixMode()
                == CliOptions.FixMode.APPLY);
    }
}
