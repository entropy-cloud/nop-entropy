package io.nop.lint.core.engine;

import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.lang.TreeSitterLanguageAdapter;
import io.nop.lint.core.node.LintNode;
import io.nop.lint.core.node.LintTree;
import io.nop.lint.core.node.SourceRange;
import io.nop.lint.core.rule.RuleDslModel;
import io.nop.lint.core.rule.RuleDslParser;
import io.nop.core.model.object.DynamicObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wiring proof for the kind-bit filter (plan Phase 2 exit criterion): a rule
 * whose target kinds are absent from the file is counted as
 * {@code rulesKindFiltered} and produces no diagnostics, while
 * {@code rulesExecuted} stays untouched for it — the two counters are
 * mutually exclusive per rule, which is only possible if filtering happens
 * before the match call, not by filtering matches afterwards.
 */
public class TestRuleSetRunner {

    private static final LintLanguage JAVA = new TreeSitterLanguageAdapter("java",
            io.nop.treesitter.language.Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin"), null);

    private static final String PRINT_SRC = "class Demo { void m() { System.out.println(\"x\"); } }";

    private final RuleDslParser parser = new RuleDslParser();

    @Test
    public void kindFilterShortCircuitsBeforeMatching() {
        CompiledRule executing = CompiledRule.compile(patternRule("demo/executes", "System.out.println($$$ARGS)"), JAVA);
        CompiledRule filtered = CompiledRule.compile(kindRule("demo/filtered", "throw_statement"), JAVA);
        LintTree tree = JAVA.parse(PRINT_SRC);

        LintStats.Builder stats = LintStats.builder();
        List<Diagnostic> diagnostics = RuleSetRunner.run(List.of(filtered, executing), tree, stats,
                LintProfile.STANDARD, FileBudget.withoutLimits(), DeepResolvers.NONE, false, null);
        LintStats result = stats.rulesLoaded(2).build();

        assertEquals(1, result.getRulesKindFiltered(), "the disjoint rule must be counted as kind-filtered");
        assertEquals(1, result.getRulesExecuted(), "the disjoint rule must not consume the executed count");
        assertEquals(1, result.getDiagnostics());
        assertEquals(1, diagnostics.size());
        assertEquals("demo/executes", diagnostics.get(0).ruleId(),
                "only the rule whose target kinds occur may produce diagnostics");
    }

    @Test
    public void allFilteredYieldsEmptyDiagnostics() {
        CompiledRule filtered = CompiledRule.compile(kindRule("demo/filtered", "throw_statement"), JAVA);
        LintTree tree = JAVA.parse(PRINT_SRC);

        LintStats.Builder stats = LintStats.builder();
        List<Diagnostic> diagnostics = RuleSetRunner.run(List.of(filtered), tree, stats, LintProfile.STANDARD, FileBudget.withoutLimits(), DeepResolvers.NONE, false, null);
        LintStats result = stats.build();

        assertEquals(0, diagnostics.size());
        assertEquals(0, result.getDiagnostics());
        assertEquals(1, result.getRulesKindFiltered());
        assertEquals(0, result.getRulesExecuted());
    }

    @Test
    public void diagnosticsCarryRuleIdentityAndNodeRange() {
        CompiledRule rule = CompiledRule.compile(patternRule("demo/p", "System.out.println($$$ARGS)"), JAVA);
        LintTree tree = JAVA.parse(PRINT_SRC);

        List<Diagnostic> diagnostics = RuleSetRunner.run(List.of(rule), tree, LintStats.builder(),
                LintProfile.STANDARD, FileBudget.withoutLimits(), DeepResolvers.NONE, false, null);

        assertEquals(1, diagnostics.size());
        Diagnostic diagnostic = diagnostics.get(0);
        assertEquals("demo/p", diagnostic.ruleId());
        assertEquals("warning", diagnostic.severity());
        assertEquals("msg demo/p", diagnostic.message());

        SourceRange expected = null;
        for (LintNode node : tree.root()) {
            if ("System.out.println(\"x\")".equals(node.text())) {
                expected = node.range();
            }
        }
        assertEquals(expected, diagnostic.range(), "the diagnostic range must be the matched node's range");
    }

    private RuleDslModel patternRule(String id, String pattern) {
        return parser.parseRuleModel(ruleModel(id, b -> b.addProp("pattern", pattern)));
    }

    private RuleDslModel kindRule(String id, String kind) {
        return parser.parseRuleModel(ruleModel(id, b -> b.addProp("kind", kind)));
    }

    private DynamicObject ruleModel(String id, java.util.function.Consumer<DynamicObject> ruleConfigurer) {
        DynamicObject model = new DynamicObject("lint-rule");
        model.addProp("id", id);
        model.addProp("language", "Java");
        model.addProp("severity", "warning");
        model.addProp("message", "msg " + id);
        DynamicObject rule = new DynamicObject("rule");
        ruleConfigurer.accept(rule);
        model.addProp("rule", rule);
        return model;
    }
}
