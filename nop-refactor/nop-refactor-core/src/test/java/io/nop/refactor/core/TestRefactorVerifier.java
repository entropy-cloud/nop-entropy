package io.nop.refactor.core;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.lint.core.engine.LanguageRegistry;
import io.nop.lint.core.engine.LintEngine;
import io.nop.lint.core.engine.LintProfile;
import io.nop.lint.core.fix.EditPlanApplier;
import io.nop.lint.core.fix.Fix;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.lang.TreeSitterLanguageAdapter;
import io.nop.lint.core.node.SourceRange;
import io.nop.lint.core.rule.RuleDslModel;
import io.nop.lint.core.rule.RuleDslParser;
import io.nop.core.model.object.DynamicObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The verification computation matrix (nop-refactor WI5 Phase 2): the
 * parseOk predicate and error-node counting over broken/clean samples, the
 * residual-lint subset (configured vs unconfigured being machine-readably
 * distinct), the conflict nonApplied production through the WI4 entry's
 * skipped list, diff assembly, and the full assembly from an applied edit
 * plan to a complete RefactorResult — including the guard-rollback face.
 */
public class TestRefactorVerifier {

    private static final LintLanguage JAVA = new TreeSitterLanguageAdapter("java",
            io.nop.treesitter.language.Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin"),
            null);

    private final RuleDslParser parser = new RuleDslParser();

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

    private RuleDslModel reportingRule() {
        DynamicObject model = new DynamicObject("lint-rule");
        model.addProp("id", "demo/residual");
        model.addProp("severity", "warning");
        model.addProp("message", "no raw println");
        DynamicObject rule = new DynamicObject("rule");
        rule.addProp("pattern", "System.out.println($$$A)");
        model.addProp("rule", rule);
        return parser.parseRuleModel(model);
    }

    private EditedFile file(String name, String original, String edited) {
        return new EditedFile(name, original.getBytes(StandardCharsets.UTF_8),
                edited.getBytes(StandardCharsets.UTF_8), 1);
    }

    @Test
    public void brokenSyntaxFailsParseWithCountedRecoveryNodes() {
        RefactorVerifier verifier = new RefactorVerifier(JAVA);
        Verification verification = verifier.verify(
                List.of(file("Broken.java", "orig", "class Demo { void m() { grobl } }")));

        assertFalse(verification.parseOk(), "the recovery node makes the file un-parse-ok");
        assertTrue(verification.errorNodeCount() > 0, "the recovery node is counted");
        assertNull(verification.symbolIntact(), "the codemod face never fills symbolIntact");
    }

    @Test
    public void cleanSyntaxParsesWithZeroRecoveryNodes() {
        RefactorVerifier verifier = new RefactorVerifier(JAVA);
        Verification verification = verifier.verify(List.of(
                file("Clean.java", "orig", "class Demo { void m() { ok(); } }")));

        assertTrue(verification.parseOk());
        assertEquals(0, verification.errorNodeCount());
    }

    @Test
    public void alreadyBrokenSourceReportsItsOwnStateNotADelta() {
        RefactorVerifier verifier = new RefactorVerifier(JAVA);
        // the pre-edit content was already broken; the per-file predicate is
        // the file's own post-edit state, not a delta against the pre-parse
        Verification verification = verifier.verify(
                List.of(file("StillBroken.java", "orig", "class Demo { void m() { grobl } }")));

        assertFalse(verification.parseOk(),
                "per-file parseOk reflects the file itself (the pre/post delta is the "
                        + "applier's rollback guard, a different face)");
    }

    @Test
    public void residualSubsetCountsExactFindings() {
        LanguageRegistry registry = LanguageRegistry.discoverDefaults();
        LintEngine engine = new LintEngine(registry, LintProfile.STANDARD);
        RefactorVerifier verifier = new RefactorVerifier(JAVA, engine, List.of(reportingRule()));

        Verification verification = verifier.verify(List.of(
                file("R.java", "orig", "class Demo { void m() { System.out.println(\"x\"); } }")));

        assertEquals(1, verification.residualDiagnostics(),
                "the subset finds exactly the println finding");
    }

    @Test
    public void unconfiguredSubsetIsDistinctFromConfiguredClean() {
        RefactorVerifier unconfigured = new RefactorVerifier(JAVA);
        Verification unconfiguredResult = unconfigured.verify(
                List.of(file("A.java", "orig", "class Demo { void m() { ok(); } }")));
        assertEquals(0, unconfiguredResult.residualDiagnostics());
        assertEquals(0, unconfigured.residualRuleCount(),
                "unconfigured: the zero means 'no subset'");

        LanguageRegistry registry = LanguageRegistry.discoverDefaults();
        LintEngine engine = new LintEngine(registry, LintProfile.STANDARD);
        RefactorVerifier configured = new RefactorVerifier(JAVA, engine, List.of(reportingRule()));
        Verification configuredResult = configured.verify(
                List.of(file("B.java", "orig", "class Demo { void m() { ok(); } }")));
        assertEquals(0, configuredResult.residualDiagnostics());
        assertEquals(1, configured.residualRuleCount(),
                "configured and clean: the stats face tells the two zeros apart");
    }

    @Test
    public void conflictNonAppliesComeFromTheEntrySkippedList() {
        // two edits on the same range: the merge keeps the first, the second
        // rides the skipped list — the nonApplied(CONFLICT) context source
        String source = "class Demo { void m() { foo(); } }";
        Fix first = new Fix(new SourceRange(source.indexOf("foo"), source.indexOf("foo") + 3),
                "bar", "demo/first", "first rewrite", 0);
        Fix second = new Fix(first.range(), "baz", "demo/second", "second rewrite", 1);

        EditPlanApplier.EditPlanResult result = EditPlanApplier.apply(
                dir.resolve("C.java"), source.getBytes(StandardCharsets.UTF_8),
                List.of(first, second), JAVA, true);

        assertEquals(1, result.skippedEdits().size());
        assertEquals("demo/second", result.skippedEdits().get(0).ruleId());

        List<NonApply> nonApplies = result.skippedEdits().stream()
                .map(skipped -> new NonApply(NonApply.Reason.CONFLICT, "C.java",
                        "overlaps an earlier-priority edit from '" + skipped.ruleId() + "'"))
                .toList();
        assertEquals(NonApply.Reason.CONFLICT, nonApplies.get(0).reason());
        assertEquals(1, RefactorStats.SkippedBuckets.of(nonApplies).conflict());
    }

    @Test
    public void assembleProducesTheCompleteResultFromAnAppliedPlan() {
        // the end-to-end face: per-file edit plan applied dry-run through the
        // WI4 entry, then verified and assembled into a RefactorResult
        String original = "class Demo { void m() { foo(1); } }";
        String edited = "class Demo { void m() { bar(1); } }";
        Fix rewrite = new Fix(new SourceRange(original.indexOf("foo"), original.indexOf("foo") + 3),
                "bar", "demo/rewrite", "rewrite foo to bar", 0);
        EditPlanApplier.EditPlanResult plan = EditPlanApplier.apply(
                dir.resolve("D.java"), original.getBytes(StandardCharsets.UTF_8),
                List.of(rewrite), JAVA, true);
        assertFalse(plan.rolledBack());

        EditedFile file = new EditedFile("D.java", original.getBytes(StandardCharsets.UTF_8),
                plan.finalSource(), plan.appliedEdits());
        RefactorVerifier verifier = new RefactorVerifier(JAVA);
        RefactorResult result = verifier.assemble(true, List.of(file),
                List.of(new FileEdit("D.java", rewrite.range(), "rewrite foo to bar")),
                List.of());

        assertTrue(result.applied());
        assertEquals(1, result.edits().size());
        assertTrue(result.diff().contains("-") && result.diff().contains("+"),
                "the diff face carries the before/after lines: " + result.diff());
        assertTrue(result.verification().parseOk());
        assertEquals(0, result.verification().errorNodeCount());
        assertEquals(1, result.stats().filesAffected());
        assertEquals(1, result.stats().editsApplied());
        assertEquals(0, result.stats().skipped().total());
        assertEquals(RefactorStats.CostTier.IN_PROCESS, result.stats().costTier());
        assertEquals(0, result.stats().residualRuleCount());
        assertTrue(result.nonApplied().isEmpty());
    }

    @Test
    public void rollbackFaceVerifiesTheRestoredContent() {
        // the edit breaks the syntax: the WI4 entry rolls back, and the
        // verification computes over the restored (original) content
        String original = "class Demo { void m() { foo(); } }";
        Fix breaking = new Fix(new SourceRange(original.indexOf("foo();"),
                original.indexOf("foo();") + 6), "grobl", "demo/break", "breaks", 0);
        EditPlanApplier.EditPlanResult plan = EditPlanApplier.apply(
                dir.resolve("E.java"), original.getBytes(StandardCharsets.UTF_8),
                List.of(breaking), JAVA, true);
        assertTrue(plan.rolledBack());
        assertEquals(0, plan.appliedEdits());

        RefactorVerifier verifier = new RefactorVerifier(JAVA);
        EditedFile file = new EditedFile("E.java", original.getBytes(StandardCharsets.UTF_8),
                plan.finalSource(), plan.appliedEdits());
        Verification verification = verifier.verify(List.of(file));

        assertTrue(verification.parseOk(),
                "verification runs over the rolled-back (restored) content");
        assertEquals(0, verification.errorNodeCount());
        assertFalse(file.changed(), "restored content equals the original: no diff line");
    }
}
