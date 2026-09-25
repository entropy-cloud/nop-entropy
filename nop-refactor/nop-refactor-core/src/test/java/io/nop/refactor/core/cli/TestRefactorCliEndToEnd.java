package io.nop.refactor.core.cli;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.refactor.core.NonApply;
import io.nop.refactor.core.RefactorResult;
import io.nop.refactor.core.RefactorVerifier;
import io.nop.lint.core.engine.LanguageRegistry;
import io.nop.lint.core.engine.LintProfile;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The refactor CLI's end-to-end matrix (nop-refactor WI7): preview and apply
 * over fixture rulesets with captured stdout/stderr and exit codes — the
 * three-state boundary (0 full / 1 partial with nonApplied / 2 abort), the
 * load gate (mixed ruleset, profile-gated rule), the exemption face, guard
 * rollback on both modes, dry-run writing nothing, and the json/console
 * payload identity. Targets live under the module dir ({@code target/...})
 * because the path grammar constrains rewrites to the working directory.
 */
public class TestRefactorCliEndToEnd {

    private static final String TRANSFORM_PREFIX = "/test/lint/cli-rules-transform";
    private static final String CONFLICT_PREFIX = "/test/lint/refactor-cli-conflict";
    private static final String ROLLBACK_PREFIX = "/test/lint/refactor-cli-rollback";
    private static final String MIXED_PREFIX = "/test/lint/refactor-cli-mixed";
    private static final String REQ_PREFIX = "/test/lint/refactor-cli-req";

    private Path root;

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
        int code = NopRefactorCli.runFull(args, LanguageRegistry.discoverDefaults(),
                LintProfile.STANDARD, new PrintStream(out), new PrintStream(err));
        return new Run(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private Path writeTarget(String relPath, String content) throws Exception {
        Path file = Path.of("target", "refactor-cli-test", relPath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return file;
    }

    // ==================== preview ====================

    @Test
    public void previewRendersDiffWritesNothingAndExitsZero() throws Exception {
        Path file = writeTarget("src/Ok.java",
                "class Ok { void m() { foo(1); inlineCall(2); } }\n");
        Run run = run("preview", "--rules", TRANSFORM_PREFIX, file.toString());

        assertEquals(0, run.exitCode(), run.stdout + run.stderr);
        assertTrue(run.stdout().contains("+") && run.stdout().contains("-"),
                "the unified diff is the console face: " + run.stdout());
        assertTrue(run.stdout().contains("dry-run"), run.stdout());
        assertEquals("class Ok { void m() { foo(1); inlineCall(2); } }\n",
                Files.readString(file), "preview never touches the file");
    }

    @Test
    public void conflictSampleExitsOneWithStructuredEntry() throws Exception {
        Path file = writeTarget("conflict/C.java",
                "class C { void m() { foo(1); } }\n");
        Run run = run("preview", "--rules", CONFLICT_PREFIX, file.toString());

        assertEquals(1, run.exitCode(), run.stdout + run.stderr);
        assertTrue(run.stdout().contains("[CONFLICT]"), run.stdout());
        assertTrue(run.stdout().contains("overlaps"), run.stdout());
    }

    @Test
    public void exemptedRuleIsOutOfScopeAndFileKeptForThatRule() throws Exception {
        Path file = writeTarget("legacy/X.java",
                "class X { void m() { System.out.println(\"x\"); inlineCall(2); } }\n");
        Run run = run("preview", "--rules", TRANSFORM_PREFIX, file.toString());

        assertEquals(1, run.exitCode(), run.stdout + run.stderr);
        assertTrue(run.stdout().contains("[OUT_OF_SCOPE]"), run.stdout());
        assertTrue(run.stdout().contains("exempted"), run.stdout());
    }

    @Test
    public void unsupportedExtensionIsAStructuredOutOfScope() throws Exception {
        Path file = writeTarget("src/notes.md", "# notes\n");
        Run run = run("preview", "--rules", TRANSFORM_PREFIX, file.toString());

        assertEquals(1, run.exitCode(), run.stdout + run.stderr);
        assertTrue(run.stdout().contains("[OUT_OF_SCOPE]"), run.stdout());
        assertTrue(run.stdout().contains("unsupported extensions"), run.stdout());
    }

    @Test
    public void targetWithoutRegisteredBindingIsStructuredOutOfScope() throws Exception {
        // the extension table classifies against the registry's registered
        // bindings: a target whose binding is missing lands in the explicit
        // skipped face (structured OUT_OF_SCOPE, never a silent drop) — the
        // abort face (2) is for IO failures and load-gate violations
        Path file = writeTarget("src/S.ts", "let x = 1;\n");
        Run run = run("preview", "--rules", TRANSFORM_PREFIX, file.toString());

        assertEquals(1, run.exitCode(), run.stdout + run.stderr);
        assertTrue(run.stdout().contains("[OUT_OF_SCOPE]"), run.stdout());
    }

    @Test
    public void previewGuardRollbackExitsOneWithRolledBackFace() throws Exception {
        Path file = writeTarget("rollback/B.java",
                "class B { void m() { foo(); } }\n");
        Run run = run("preview", "--rules", ROLLBACK_PREFIX, file.toString());

        assertEquals(1, run.exitCode(), run.stdout + run.stderr);
        assertTrue(run.stdout().contains("[ROLLED_BACK]"), run.stdout());
        assertEquals("class B { void m() { foo(); } }\n", Files.readString(file),
                "the rollback restored the content");
    }

    @Test
    public void mixedRulesetFailsTheLoadGate() throws Exception {
        Path file = writeTarget("mixed/M.java", "class M { void m() { foo(1); } }\n");
        Run run = run("preview", "--rules", MIXED_PREFIX, file.toString());

        assertEquals(2, run.exitCode(), run.stderr);
        assertTrue(run.stderr().contains("transform rule"), run.stderr());
    }

    @Test
    public void profileGatedRuleFailsTheLoadGate() throws Exception {
        Path file = writeTarget("req/R.java", "class R { void m() { foo(1); } }\n");
        Run run = run("preview", "--rules", REQ_PREFIX, file.toString());

        assertEquals(2, run.exitCode(), run.stderr);
        assertTrue(run.stderr().contains("requires"), run.stderr());
    }

    @Test
    public void illegalArgumentsExitTwoWithUsage() {
        Run run = run("frobnicate");
        assertEquals(2, run.exitCode());
        assertTrue(run.stderr().contains("usage: nop-refactor"), run.stderr());
    }

    @Test
    public void missingRulesetPrefixExitsTwo() {
        Run run = run("preview", "--rules", "/test/lint/does-not-exist",
                "target/refactor-cli-test/src/Ok.java");
        assertEquals(2, run.exitCode());
    }

    @Test
    public void jsonFaceCarriesTheSamePayload() throws Exception {
        Path file = writeTarget("src/Json.java",
                "class Json { void m() { foo(1); inlineCall(2); } }\n");
        Run console = run("preview", "--rules", TRANSFORM_PREFIX, file.toString());
        Run json = run("preview", "--json", "--rules", TRANSFORM_PREFIX, file.toString());

        assertEquals(console.exitCode(), json.exitCode());
        assertTrue(json.stdout().contains("\"applied\": false"), json.stdout());
        assertTrue(json.stdout().contains("\"diff\""), json.stdout());
        assertTrue(json.stdout().contains("\"nonApplied\""), json.stdout());
        assertFalse(console.stdout().isEmpty());
    }

    // ==================== apply ====================

    @Test
    public void applyWritesFilesAndExitsZero() throws Exception {
        Path file = writeTarget("apply/A.java",
                "class A { void m() { System.out.println(\"x\"); inlineCall(2); } }\n");
        Run run = run("apply", "--rules", TRANSFORM_PREFIX, file.toString());

        assertEquals(0, run.exitCode(), run.stdout + run.stderr);
        assertEquals("class A { void m() { logger.log(\"x\"); directCall(2); } }\n",
                Files.readString(file), "apply lands both rewrites");
        assertTrue(run.stdout().contains("(written)"), run.stdout());
    }

    @Test
    public void applyGuardRollbackRestoresAndExitsOne() throws Exception {
        Path file = writeTarget("rollback/BA.java",
                "class BA { void m() { foo(); } }\n");
        Run run = run("apply", "--rules", ROLLBACK_PREFIX, file.toString());

        assertEquals(1, run.exitCode(), run.stdout + run.stderr);
        assertEquals("class BA { void m() { foo(); } }\n", Files.readString(file),
                "the rollback restored the pre-edit content on disk");
        assertTrue(run.stdout().contains("[ROLLED_BACK]"), run.stdout());
    }

    @Test
    public void applyPartialConflictAppliesAndReports() throws Exception {
        Path file = writeTarget("conflict/CA.java",
                "class CA { void m() { foo(1); } }\n");
        Run run = run("apply", "--rules", CONFLICT_PREFIX, file.toString());

        assertEquals(1, run.exitCode(), run.stdout + run.stderr);
        assertEquals("class CA { void m() { bar(1); } }\n", Files.readString(file),
                "the earlier-generation rewrite lands");
        assertTrue(run.stdout().contains("[CONFLICT]"), run.stdout());
    }

    // ==================== exit mapping component face ====================

    @Test
    public void exitMappingGridIsAnchored() {
        RefactorResult empty = new RefactorVerifier(LanguageRegistry.discoverDefaults()
                .resolve("java")).assemble(true, List.of(), List.of(), List.of());

        assertEquals(NopRefactorCli.EXIT_OK, NopRefactorCli.exitFor(0, empty),
                "empty nonApplied = full success");
        assertEquals(NopRefactorCli.EXIT_INTERNAL, NopRefactorCli.exitFor(2, empty),
                "a degraded transform generation is an abort (sample built through the "
                        + "public incTransformDegraded builder contract)");
        RefactorResult partial = new RefactorVerifier(LanguageRegistry.discoverDefaults()
                .resolve("java")).assemble(true, List.of(), List.of(),
                List.of(new NonApply(NonApply.Reason.CONFLICT, "a.java", "overlaps demo/x")));
        assertEquals(NopRefactorCli.EXIT_NONAPPLIED, NopRefactorCli.exitFor(0, partial),
                "any nonApplied entry = structured partial");
    }
}
