package io.nop.lint.core.engine;

import io.nop.lint.core.fix.Fix;
import io.nop.lint.core.fix.FixApplier;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.lang.TreeSitterLanguageAdapter;
import io.nop.lint.core.node.LintTree;
import io.nop.lint.core.node.SourceRange;
import io.nop.lint.core.rule.RuleDslModel;
import io.nop.lint.core.rule.RuleDslParser;
import io.nop.core.model.object.DynamicObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The transform channel's engine semantics (nop-refactor WI3 Phase 2): a
 * transform match lands on the rewrite-only channel with its own counters,
 * the fast profile closes the generation as a counted degrade, and the
 * single merge point keeps the generation-ordinal priority across the
 * diagnostic-fix and rewrite channels.
 */
public class TestTransformEngine {

    private static final LintLanguage JAVA = new TreeSitterLanguageAdapter("java",
            io.nop.treesitter.language.Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin"),
            null);

    private final RuleDslParser parser = new RuleDslParser();

    private RuleDslModel transformModel(String id, String pattern, String template) {
        DynamicObject model = new DynamicObject("lint-rule");
        model.addProp("id", id);
        DynamicObject rule = new DynamicObject("rule");
        rule.addProp("pattern", pattern);
        model.addProp("rule", rule);
        DynamicObject transform = new DynamicObject("transform");
        transform.addProp("description", "rewrite " + id);
        transform.addProp("template", template);
        model.addProp("transform", transform);
        return parser.parseRuleModel(model);
    }

    private RuleDslModel fixModel(String id, String pattern, String template) {
        DynamicObject model = new DynamicObject("lint-rule");
        model.addProp("id", id);
        model.addProp("severity", "warning");
        model.addProp("message", "fix " + id);
        DynamicObject rule = new DynamicObject("rule");
        rule.addProp("pattern", pattern);
        model.addProp("rule", rule);
        DynamicObject fix = new DynamicObject("fix");
        fix.addProp("description", "rewrite " + id);
        fix.addProp("template", template);
        model.addProp("fix", fix);
        return parser.parseRuleModel(model);
    }

    @Test
    public void transformMatchProducesRewriteOnlyChannel() {
        LintTree tree = JAVA.parse("class Demo { void m() { foo(1); } }");
        CompiledRule compiled = CompiledRule.compile(
                transformModel("demo/t", "foo($$$A)", "bar($$$A)"), JAVA);

        RuleSetRunner.RunResult result = RuleSetRunner.runWithRewrites(List.of(compiled), tree,
                LintStats.builder(), LintProfile.STANDARD, FileBudget.withoutLimits(),
                DeepResolvers.NONE, false, null, null);

        assertTrue(result.diagnostics().isEmpty(),
                "a transform match never produces a diagnostic");
        assertEquals(1, result.transformFixes().size());
        assertEquals("bar(1)", result.transformFixes().get(0).replacement());
        assertEquals("demo/t", result.transformFixes().get(0).ruleId());
    }

    @Test
    public void fastProfileClosesTransformGenerationAsCountedDegrade() {
        LintTree tree = JAVA.parse("class Demo { void m() { foo(1); } }");
        CompiledRule compiled = CompiledRule.compile(
                transformModel("demo/t", "foo($$$A)", "bar($$$A)"), JAVA);
        LintStats.Builder stats = LintStats.builder();

        RuleSetRunner.RunResult result = RuleSetRunner.runWithRewrites(List.of(compiled), tree,
                stats, LintProfile.FAST, FileBudget.withoutLimits(), DeepResolvers.NONE, false, null, null);

        assertTrue(result.transformFixes().isEmpty(),
                "the fast profile never opens the generation surface");
        assertEquals(0, stats.build().getTransformDegraded(),
                "fast's closed-by-profile surface is not a degradation event — same "
                        + "accounting as the fix carrier (only the ladder closure degrades)");
        assertEquals(1, stats.build().getTransformMatches(),
                "the match itself still happened and is counted");
    }

    @Test
    public void mergedCandidatesKeepGenerationOrderAcrossChannels() {
        // a diagnostic fix (order 0) and a transform rewrite (order 1)
        // targeting the same range: the earlier generation wins the merge,
        // the later one is a counted conflict — the list-order priority the
        // single Fixer authority consumes
        LintTree tree = JAVA.parse("class Demo { void m() { foo(1); } }");
        CompiledRule fixRule = CompiledRule.compile(
                fixModel("demo/fx", "foo($$$A)", "logged($$$A)"), JAVA);
        CompiledRule transformRule = CompiledRule.compile(
                transformModel("demo/tt", "foo($$$A)", "bar($$$A)"), JAVA);
        LintStats.Builder stats = LintStats.builder();

        RuleSetRunner.RunResult result = RuleSetRunner.runWithRewrites(
                List.of(fixRule, transformRule), tree, stats, LintProfile.STANDARD,
                FileBudget.withoutLimits(), DeepResolvers.NONE, false, null, null);

        assertEquals(1, result.diagnostics().size());
        assertEquals(0, result.diagnostics().get(0).fix().order());
        assertEquals(1, result.transformFixes().size());
        assertEquals(1, result.transformFixes().get(0).order());

        // the applier merge: the diagnostic fix (earlier ordinal) survives,
        // the overlapping rewrite is a counted conflict
        FixApplier applier = new FixApplier(
                source -> new LintResult(result.diagnostics(), stats.build(),
                        result.transformFixes()),
                JAVA);
        byte[] source = "class Demo { void m() { foo(1); } }".getBytes(StandardCharsets.UTF_8);
        FixApplier.FixResult fixResult = applier.run(Path.of("x.java"), source, true);

        assertEquals(1, fixResult.stats().applied(), "the earlier generation survives");
        assertEquals(1, fixResult.stats().conflicts(), "the overlapping rewrite is a conflict");
        assertTrue(new String(fixResult.finalSource(), StandardCharsets.UTF_8)
                .contains("logged(1)"));
    }

    @Test
    public void nonOverlappingChannelsBothApply() {
        LintTree tree = JAVA.parse("class Demo { void m() { foo(1); inlineCall(2); } }");
        CompiledRule fixRule = CompiledRule.compile(
                fixModel("demo/fx2", "inlineCall($$$A)", "direct($$$A)"), JAVA);
        CompiledRule transformRule = CompiledRule.compile(
                transformModel("demo/t2", "foo($$$A)", "bar($$$A)"), JAVA);

        RuleSetRunner.RunResult result = RuleSetRunner.runWithRewrites(
                List.of(fixRule, transformRule), tree, LintStats.builder(), LintProfile.STANDARD,
                FileBudget.withoutLimits(), DeepResolvers.NONE, false, null, null);

        FixApplier applier = new FixApplier(
                source -> new LintResult(result.diagnostics(), LintStats.builder().build(),
                        result.transformFixes()),
                JAVA);
        byte[] source = "class Demo { void m() { foo(1); inlineCall(2); } }"
                .getBytes(StandardCharsets.UTF_8);
        FixApplier.FixResult fixResult = applier.run(Path.of("y.java"), source, true);

        assertEquals(2, fixResult.stats().applied(),
                "disjoint edits from both channels apply in one plan");
        assertEquals(0, fixResult.stats().conflicts());
        String out = new String(fixResult.finalSource(), StandardCharsets.UTF_8);
        assertTrue(out.contains("bar(1)") && out.contains("direct(2)"), out);
    }
}
