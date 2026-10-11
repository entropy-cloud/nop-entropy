package io.nop.refactor.core.operation;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.lint.core.cli.RuleSetLoader;
import io.nop.lint.core.engine.LanguageRegistry;
import io.nop.lint.core.engine.LintEngine;
import io.nop.lint.core.engine.LintProfile;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.node.SourceRange;
import io.nop.lint.core.suppress.ExemptionFilter;
import io.nop.refactor.core.NonApply;
import io.nop.refactor.core.RefactorResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The WI9 wiring proofs (plan 09 adjudication 6, Minimum Rules #22/#23):
 * the codemod operation runs the full framework lifecycle — check, plan,
 * the single WI4 apply, the single WI5 assemble — with real fixture rulesets
 * and real files, including the conflict and guard-rollback nonApplied
 * paths; and a test-local fixture operation that is not a rewrite rides the
 * same runner unchanged, proving the framework is operation-agnostic (the
 * property the rename operations from WI10 on will inherit).
 */
public class TestOperationFrameworkWiring {

    private static final String TRANSFORM_PREFIX = "/test/lint/cli-rules-transform";
    private static final String CONFLICT_PREFIX = "/test/lint/refactor-cli-conflict";
    private static final String ROLLBACK_PREFIX = "/test/lint/refactor-cli-rollback";

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private static Path writeTarget(String relPath, String content) throws Exception {
        Path file = Path.of("target", "operation-wiring", relPath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private static RewriteRequest request(String prefix, LanguageRegistry registry,
                                          PreparedTarget... targets) {
        RuleSetLoader.LoadedRuleSet loaded = new RuleSetLoader().loadRuleSet(prefix);
        return new RewriteRequest(loaded, ExemptionFilter.of(loaded.exemptions()),
                new LintEngine(registry, LintProfile.STANDARD), registry,
                List.of(targets), List.of(), prefix);
    }

    // ==================== the codemod operation end to end ====================

    @Test
    void rewriteOperationRunsTheFullFrameworkLifecycle() throws Exception {
        LanguageRegistry registry = LanguageRegistry.discoverDefaults();
        Path file = writeTarget("e2e/W.java", "class W { void m() { inlineCall(1); } }\n");
        PreparedTarget target = new PreparedTarget(file, "java",
                Files.readAllBytes(file));

        RefactorResult preview = RefactorOperationRunner.INSTANCE.run(
                RewriteOperation.INSTANCE, request(TRANSFORM_PREFIX, registry, target), true);
        assertFalse(preview.applied(), "dry-run reports applied=false");
        assertTrue(preview.diff().contains("directCall"),
                "the diff carries the plan's rewrite: " + preview.diff());
        assertTrue(preview.verification().parseOk(), "the planned content re-parses");
        assertEquals("class W { void m() { inlineCall(1); } }\n", Files.readString(file),
                "dry-run wrote nothing");

        RefactorResult applied = RefactorOperationRunner.INSTANCE.run(
                RewriteOperation.INSTANCE, request(TRANSFORM_PREFIX, registry, target), false);
        assertTrue(applied.applied(), "apply reports applied=true");
        assertEquals(1, applied.stats().editsApplied());
        assertEquals("class W { void m() { directCall(1); } }\n", Files.readString(file),
                "apply landed through the single WI4 entry");
        assertEquals(1, applied.edits().size());
        assertEquals("Rewrite inline call to direct call", applied.edits().get(0).summary());
        assertEquals(0, applied.verification().errorNodeCount());
        assertEquals(0, applied.nonApplied().size());
    }

    @Test
    void conflictAndRollbackSurfaceAsStructuredNonApplies() throws Exception {
        LanguageRegistry registry = LanguageRegistry.discoverDefaults();

        Path conflictFile = writeTarget("conflict/C.java",
                "class C { void m() { foo(1); } }\n");
        RefactorResult conflict = RefactorOperationRunner.INSTANCE.run(
                RewriteOperation.INSTANCE, request(CONFLICT_PREFIX, registry,
                        new PreparedTarget(conflictFile, "java",
                                Files.readAllBytes(conflictFile))), true);
        assertEquals(1, conflict.stats().skipped().conflict(),
                "the losing overlapping edit is counted: " + conflict.stats());
        assertEquals(1, conflict.nonApplied().size());
        assertEquals(NonApply.Reason.CONFLICT, conflict.nonApplied().get(0).reason());
        assertTrue(conflict.nonApplied().get(0).detail().contains("rewrite-b"),
                conflict.nonApplied().get(0).detail());
        assertEquals(1, conflict.edits().size(), "the winning edit survives");

        Path rollbackFile = writeTarget("rollback/R.java",
                "class R { void m() { foo(); } }\n");
        RefactorResult rollback = RefactorOperationRunner.INSTANCE.run(
                RewriteOperation.INSTANCE, request(ROLLBACK_PREFIX, registry,
                        new PreparedTarget(rollbackFile, "java",
                                Files.readAllBytes(rollbackFile))), false);
        assertEquals(1, rollback.stats().skipped().rolledBack());
        assertEquals(NonApply.Reason.ROLLED_BACK, rollback.nonApplied().get(0).reason());
        assertEquals(0, rollback.edits().size(), "rolled-back edits leave no payload edits");
        assertEquals("class R { void m() { foo(); } }\n", Files.readString(rollbackFile),
                "the guard restored the original content");
    }

    @Test
    void infeasibleRulesetIsRejectedByCheck() {
        LanguageRegistry registry = LanguageRegistry.discoverDefaults();
        RewriteRequest mixed = request("/test/lint/refactor-cli-mixed", registry);
        try {
            RefactorOperationRunner.INSTANCE.run(RewriteOperation.INSTANCE, mixed, true);
            throw new AssertionError("the load gate must reject a mixed ruleset");
        } catch (Exception e) {
            assertTrue(String.valueOf(e).contains("transform")
                            || String.valueOf(e).contains("rule"),
                    "the rejection names the gate: " + e);
        }
    }

    // ==================== the framework is operation-agnostic ====================

    /**
     * A minimal non-rewrite operation: plans one fixed byte-range edit on a
     * marker identifier. It never touches RewriteRequest — and the runner
     * lands and verifies it exactly like the codemod face.
     */
    static final class MarkerOperation implements RefactorOperation<PreparedTarget> {

        static final MarkerOperation INSTANCE = new MarkerOperation();

        @Override
        public void check(PreparedTarget input) {
            if (input.content().length == 0) {
                throw new io.nop.refactor.core.NopRefactorException(
                        "empty content is not a feasible target");
            }
        }

        @Override
        public OperationPlan plan(PreparedTarget input) {
            String text = new String(input.content(), StandardCharsets.UTF_8);
            int at = text.indexOf("oldName");
            if (at < 0) {
                throw new io.nop.refactor.core.NopRefactorException(
                        "no 'oldName' marker in the target '"
                                + input.path() + "' (fail-closed, no silent no-op)");
            }
            LintLanguage lintJava = LanguageRegistry.discoverDefaults().resolve("java");
            return new OperationPlan(
                    List.of(new PlannedFile(input.path(), input.content(),
                            List.of(new io.nop.lint.core.fix.Fix(
                                    new SourceRange(at, at + "oldName".length()), "freshName",
                                    "fixture/marker", "rename the marker identifier", 0)), lintJava)),
                    java.util.Map.of(input.path().toString(), lintJava),
                    new LintEngine(LanguageRegistry.discoverDefaults(), LintProfile.STANDARD),
                    List.of());
        }
    }

    @Test
    void fixtureOperationRidesTheSameFrameworkPath() throws Exception {
        Path file = writeTarget("fixture/F.java", "class F { int oldName = 1; }\n");
        PreparedTarget target = new PreparedTarget(file, "java", Files.readAllBytes(file));

        RefactorResult result = RefactorOperationRunner.INSTANCE.run(
                MarkerOperation.INSTANCE, target, false);

        assertTrue(result.applied());
        assertEquals("class F { int freshName = 1; }\n", Files.readString(file),
                "a non-rewrite operation lands through the same single apply point");
        assertEquals(1, result.edits().size());
        assertEquals("rename the marker identifier", result.edits().get(0).summary());
        assertTrue(result.verification().parseOk());
    }
}
