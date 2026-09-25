package io.nop.lint.core.cli;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.lint.core.NopLintException;
import io.nop.lint.core.engine.LanguageRegistry;
import io.nop.lint.core.engine.LintProfile;
import io.nop.lint.core.testing.JavaBindingTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CheckRunner} assembly proofs: file discovery feeds the shared
 * {@code LintEngine} (real parse, real rules from the classpath VFS), the
 * {@link RunSummary} aggregates severities and stats across files, the
 * unbound-rule-language deployment error fails closed before any file is
 * linted, and a clean tree aggregates to a zero-violation outcome.
 */
public class TestCheckRunner {

    private static final String VALID_PREFIX = "/test/lint/cli-rules";
    private static final String UNBOUND_PREFIX = "/test/lint/cli-rules-unbound";

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

    private CheckOutcome check(LanguageRegistry registry, String rulesPrefix) {
        TargetScanner.ScanResult scan = TargetScanner.scan(List.of(dir.toString()), registry);
        return new CheckRunner(registry, new RuleSetLoader(), rulesPrefix)
                .run(scan, LintProfile.STANDARD);
    }

    @Test
    public void lintsEveryScannedFileAndAggregatesSummary() throws IOException {
        Files.writeString(dir.resolve("dirty.java"),
                """
                        class Dirty {
                            void log() {
                                System.out.println("hi");
                                throw new RuntimeException("boom");
                            }
                        }
                        """);
        Files.writeString(dir.resolve("clean.java"), "class Clean {\n}\n");
        Files.writeString(dir.resolve("notes.md"), "not linted");

        CheckOutcome outcome = check(JavaBindingTestSupport.registryWithJava(), VALID_PREFIX);

        assertEquals(2, outcome.findings().size(), "two *.java files linted");
        assertEquals(2, outcome.summary().getFilesScanned());
        assertEquals(1, outcome.summary().getSkipped().total());

        assertEquals(1, outcome.summary().getErrorCount());
        assertEquals(1, outcome.summary().getWarningCount());
        assertEquals(2, outcome.summary().getTotalDiagnostics());
        assertTrue(outcome.hasErrorDiagnostics(), "the error-severity diagnostic must flag exit 1");

        assertEquals(2, outcome.summary().getRulesLoaded(), "the two-rule fixture set");
        assertTrue(outcome.summary().getRulesExecuted() >= 2,
                "both rules must reach their matcher on the dirty file");
        assertEquals(0, outcome.summary().getSuppressedDiagnostics());
        assertTrue(outcome.summary().getDisabledRuleIds().isEmpty());
    }

    @Test
    public void cleanTreeAggregatesToZeroDiagnosticsAndExitZero() throws IOException {
        Files.writeString(dir.resolve("clean.java"), "class Clean {\n    int ok = 1;\n}\n");

        CheckOutcome outcome = check(JavaBindingTestSupport.registryWithJava(), VALID_PREFIX);

        assertTrue(outcome.findings().get(0).diagnostics().isEmpty());
        assertEquals(0, outcome.summary().getTotalDiagnostics());
        assertFalse(outcome.hasErrorDiagnostics());
    }

    @Test
    public void findingsStayInStableFileAndEngineOrder() throws IOException {
        Files.writeString(dir.resolve("b.java"),
                "class B {\n    void x() {\n        System.out.println(\"b\");\n    }\n}\n");
        Files.writeString(dir.resolve("a.java"),
                "class A {\n    void y() {\n        System.out.println(\"a\");\n    }\n}\n");

        CheckOutcome outcome = check(JavaBindingTestSupport.registryWithJava(), VALID_PREFIX);

        assertEquals(2, outcome.findings().size());
        assertTrue(outcome.findings().get(0).displayPath().endsWith("a.java"),
                "file order follows the sorted scan");
        assertEquals(1, outcome.findings().get(0).diagnostics().size());
        assertEquals("demo/no-print", outcome.findings().get(0).diagnostics().get(0).ruleId(),
                "diagnostic order follows engine rule order");
    }

    @Test
    public void unboundRuleLanguageFailsClosedBeforeAnyFileIsLinted() throws IOException {
        Files.writeString(dir.resolve("dirty.java"),
                "class Dirty {\n    void x() {\n        System.out.println(\"x\");\n    }\n}\n");

        NopLintException ex = assertThrows(NopLintException.class,
                () -> check(JavaBindingTestSupport.registryWithJava(), UNBOUND_PREFIX));
        assertTrue(ex.getMessage().contains("no language binding registered for lint language 'typescript'"),
                ex.getMessage());
        assertTrue(ex.getMessage().contains("demo/ts-only"),
                "the message must name the offending rule id: " + ex.getMessage());
    }

    @Test
    public void brokenRuleFailsTheRunAtStartupBeforeAnyFileIsRead() throws Exception {
        // plan 10: the run compiles once up front — a rule that cannot
        // compile surfaces here (named by the compile error), not at the
        // first file that happens to trip it (the old per-file timing)
        Files.writeString(dir.resolve("dirty.java"),
                "class Dirty {\n    void x() {\n        System.out.println(\"d\");\n    }\n}\n");

        // reuse the counting seam: if any file were read the counter would move
        java.util.concurrent.atomic.AtomicInteger reads =
                new java.util.concurrent.atomic.AtomicInteger();
        NopLintException ex = assertThrows(NopLintException.class,
                () -> new CheckRunner(JavaBindingTestSupport.registryWithJava(),
                        new RuleSetLoader(),
                        "/test/lint/cli-rules-compile-broken")
                        .run(TargetScanner.scan(List.of(dir.toString()),
                                JavaBindingTestSupport.registryWithJava()),
                                LintProfile.STANDARD, CliOptions.FixMode.NONE,
                                CliOptions.BaselineOp.NONE, null, null,
                                path -> {
                                    reads.incrementAndGet();
                                    try {
                                        return java.nio.file.Files.readAllBytes(path);
                                    } catch (java.io.IOException e) {
                                        throw new java.io.UncheckedIOException(e);
                                    }
                                }));
        assertTrue(ex.getMessage().contains("no_such_kind"), ex.getMessage());
        assertEquals(0, reads.get(), "no file was read — the failure precedes the loop");
    }

    @Test
    public void lintableFileWithoutRulesForItsLanguageRunsExplicitlyEmpty() throws IOException {
        // the fixture prefix holds only a TypeScript rule (bound here via a
        // stub binding): the java file is linted with loaded=0 — an explicit
        // zero, observable in the summary
        Files.writeString(dir.resolve("solo.java"), "class Solo {\n}\n");

        LanguageRegistry registry = JavaBindingTestSupport.registryWithJava();
        registry.register(JavaBindingTestSupport.stubBinding("typescript"));

        CheckOutcome outcome = check(registry, UNBOUND_PREFIX);

        assertEquals(1, outcome.summary().getFilesScanned());
        assertEquals(0, outcome.summary().getRulesLoaded());
        assertEquals(0, outcome.summary().getTotalDiagnostics());
        assertFalse(outcome.hasErrorDiagnostics());
    }
}
