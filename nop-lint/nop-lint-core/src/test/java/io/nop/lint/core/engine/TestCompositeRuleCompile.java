package io.nop.lint.core.engine;

import io.nop.core.model.object.DynamicObject;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.lang.TreeSitterLanguageAdapter;
import io.nop.lint.core.node.LintTree;
import io.nop.lint.core.rule.RuleDslModel;
import io.nop.lint.core.rule.RuleDslParser;
import io.nop.lint.core.NopLintException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Composite rule compilation (plan 2026-09-22-0544-2 Phase 2): the kind
 * opinion per design 01 §4 step 5 (all → conservative intersection,
 * relational/not → no opinion), the whole-tree node-matcher scan, and the
 * RuleSetRunner wiring — the composite's extracted kinds really feed the
 * kind-bit filter, and the scan really produces the diagnostics.
 */
public class TestCompositeRuleCompile {

    private static final LintLanguage JAVA = new TreeSitterLanguageAdapter("java",
            io.nop.treesitter.language.Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin"), null);

    private static final String SOURCE = """
            class Demo {
                void m() {
                    try {
                        a();
                    } catch (Exception e) {
                        LOG.warn("x", e.getMessage());
                    }
                }
            }
            """;

    private final RuleDslParser parser = new RuleDslParser();

    @Test
    public void allKindOpinionIsTheIntersectionOfNonEmptyOpinions() {
        RuleDslModel model = parser.parseRuleModel(ruleModel("demo/all-kinds", b -> {
            b.addProp("all", List.of(
                    matcher("kind", "catch_clause"),
                    notHas("LOG.warn($$$ARGS)")));
        }));
        CompiledRule compiled = CompiledRule.compile(model, JAVA);

        int[] targets = compiled.targetKindIds();
        assertEquals(1, targets.length, "kind contributes its singleton; not contributes no opinion");
        assertEquals(JAVA.kindId("catch_clause"), targets[0]);
        assertTrue(compiled.canMatchKinds(List.of(JAVA.kindId("catch_clause"))));
        assertTrue(!compiled.canMatchKinds(List.of(JAVA.kindId("throw_statement"))),
                "a file without catch_clause cannot match");
    }

    @Test
    public void purelyRelationalTopHasNoKindOpinion() {
        RuleDslModel model = parser.parseRuleModel(ruleModel("demo/rel-only", b ->
                b.addProp("has", relational("foo()", "end", null, null))));
        CompiledRule compiled = CompiledRule.compile(model, JAVA);

        assertEquals(0, compiled.targetKindIds().length,
                "relational candidates are kind-unconstrained: no opinion");
        assertTrue(compiled.canMatchKinds(List.of()), "no opinion must pass the filter");
    }

    @Test
    public void compositeScanProducesDiagnosticsThroughTheRunner() {
        // catch_clause that logs only the message but never rethrows
        RuleDslModel model = parser.parseRuleModel(ruleModel("demo/no-rethrow", b -> {
            b.addProp("all", List.of(
                    matcher("kind", "catch_clause"),
                    notHas("throw $$$")));
        }));
        CompiledRule compiled = CompiledRule.compile(model, JAVA);
        LintTree tree = JAVA.parse(SOURCE);

        LintStats.Builder stats = LintStats.builder();
        List<Diagnostic> diagnostics = RuleSetRunner.run(List.of(compiled), tree, stats,
                LintProfile.STANDARD);

        assertEquals(1, diagnostics.size(), "the catch clause without rethrow must be reported");
        assertEquals("demo/no-rethrow", diagnostics.get(0).ruleId());
        assertEquals("catch_clause",
                nodeKindAtRange(tree, diagnostics.get(0).range()),
                "the diagnostic anchors on the catch_clause candidate");
        assertEquals(1, stats.build().getRulesExecuted(),
                "the composite rule must really execute (not be filtered)");
    }

    @Test
    public void compositeKindFilterShortCircuitsDisjointFiles() {
        RuleDslModel model = parser.parseRuleModel(ruleModel("demo/filtered-composite", b -> {
            b.addProp("all", List.of(
                    matcher("kind", "catch_clause"),
                    notHas("throw $$$")));
        }));
        CompiledRule compiled = CompiledRule.compile(model, JAVA);
        LintTree tree = JAVA.parse("class Demo { void m() { System.out.println(\"x\"); } }");

        LintStats.Builder stats = LintStats.builder();
        List<Diagnostic> diagnostics = RuleSetRunner.run(List.of(compiled), tree, stats,
                LintProfile.STANDARD);

        assertEquals(0, diagnostics.size());
        assertEquals(1, stats.build().getRulesKindFiltered(),
                "the extracted kind opinion must feed the kind-bit filter (wiring)");
        assertEquals(0, stats.build().getRulesExecuted());
    }

    @Test
    public void stopByRuleIsRejectedAtCompileTimeUntilItem24() {
        RuleDslModel model = parser.parseRuleModel(ruleModel("demo/stop-rule", b ->
                b.addProp("has", relational("foo()", "rule", "my-util", null))));

        NopLintException ex = assertThrows(NopLintException.class,
                () -> CompiledRule.compile(model, JAVA),
                "stopBy=rule references the utils registry (item 24) and must fail closed");
        assertTrue(ex.getMessage().contains("demo/stop-rule"));
        assertTrue(ex.getMessage().contains("item 24"));
    }

    @Test
    public void regexInsideAllIsRejectedAtCompileTime() {
        RuleDslModel model = parser.parseRuleModel(ruleModel("demo/regex-in-all", b -> {
            b.addProp("all", List.of(matcher("kind", "catch_clause"), matcher("regex", ".*")));
        }));

        NopLintException ex = assertThrows(NopLintException.class, () -> CompiledRule.compile(model, JAVA));
        assertTrue(ex.getMessage().contains("demo/regex-in-all"));
        assertTrue(ex.getMessage().contains("regex"));
    }

    @Test
    public void contextualHasMatchesExpressionPositionFieldAccess() {
        // A bare `Errors.$FIELD` snippet parses as scoped_type_identifier (the
        // java grammar's type reading of `A.B` at snippet root), while the
        // same shape in expression position is a field_access — the contextual
        // form (design 01 §1) pins the parse context so the signal matches.
        // Rule shape = silent-swallow semantics: report catches with neither a
        // rethrow nor an `Errors.<field>` read (the good signals).
        RuleDslModel model = parser.parseRuleModel(ruleModel("demo/errors-field", b -> {
            b.addProp("all", List.of(
                    matcher("kind", "catch_clause"),
                    notHas("throw $$$"),
                    notMatcher(hasContextual("use(Errors.$FIELD)", "field_access", "body"))));
        }));
        CompiledRule compiled = CompiledRule.compile(model, JAVA);

        String source = """
                class Demo {
                    void n() {
                        try {
                            a();
                        } catch (Exception e) {
                            log(Errors.ERR_X + ": " + e.getMessage());
                        }
                    }
                }
                """;
        List<Diagnostic> diagnostics = RuleSetRunner.run(List.of(compiled), JAVA.parse(source),
                LintStats.builder(), LintProfile.STANDARD);
        assertEquals(0, diagnostics.size(),
                "the Errors.ERR_X field read is a good signal: the catch is not a silent swallow");

        String silent = """
                class Demo {
                    void n() {
                        try {
                            a();
                        } catch (Exception e) {
                            LOG.warn("ignored", e);
                        }
                    }
                }
                """;
        diagnostics = RuleSetRunner.run(List.of(compiled), JAVA.parse(silent),
                LintStats.builder(), LintProfile.STANDARD);
        assertEquals(1, diagnostics.size(),
                "without a signal the catch must still be reported");
    }

    @Test
    public void contextualHasRejectsOtherRootIdentifiers() {
        RuleDslModel model = parser.parseRuleModel(ruleModel("demo/errors-field-exact", b -> {
            b.addProp("all", List.of(
                    matcher("kind", "catch_clause"),
                    notHas("throw $$$"),
                    notMatcher(hasContextual("use(Errors.$FIELD)", "field_access", "body"))));
        }));
        CompiledRule compiled = CompiledRule.compile(model, JAVA);

        // OtherUtil.ERR_X is a different family: not an `Errors.` signal.
        String source = """
                class Demo {
                    void n() {
                        try {
                            a();
                        } catch (Exception e) {
                            log(OtherUtil.ERR_X + ": " + e.getMessage());
                        }
                    }
                }
                """;
        List<Diagnostic> diagnostics = RuleSetRunner.run(List.of(compiled), JAVA.parse(source),
                LintStats.builder(), LintProfile.STANDARD);
        assertEquals(1, diagnostics.size(),
                "an unrelated root identifier must not satisfy the Errors. signal");
    }

    private DynamicObject notMatcher(DynamicObject inner) {
        DynamicObject not = new DynamicObject("not");
        not.addProp("has", inner);
        DynamicObject element = new DynamicObject("matcher");
        element.addProp("not", not);
        return element;
    }

    private DynamicObject hasContextual(String context, String selector, String field) {
        DynamicObject has = new DynamicObject("has");
        has.addProp("context", context);
        has.addProp("selector", selector);
        has.addProp("stopBy", "end");
        has.addProp("field", field);
        return has;
    }

    private String nodeKindAtRange(LintTree tree, io.nop.lint.core.node.SourceRange range) {
        for (io.nop.lint.core.node.LintNode node : tree.root()) {
            if (node.range().equals(range)) {
                return node.kind();
            }
        }
        return null;
    }

    private DynamicObject matcher(String key, String value) {
        DynamicObject matcher = new DynamicObject("matcher");
        matcher.addProp(key, value);
        return matcher;
    }

    private DynamicObject notHas(String pattern) {
        DynamicObject has = new DynamicObject("has");
        has.addProp("pattern", pattern);
        has.addProp("stopBy", "end");
        DynamicObject not = new DynamicObject("not");
        not.addProp("has", has);
        DynamicObject element = new DynamicObject("matcher");
        element.addProp("not", not);
        return element;
    }

    private DynamicObject relational(String pattern, String stopBy, String stopByRule, String field) {
        DynamicObject relational = new DynamicObject("relational");
        relational.addProp("pattern", pattern);
        if (stopBy != null) {
            relational.addProp("stopBy", stopBy);
        }
        if (stopByRule != null) {
            relational.addProp("stopByRule", stopByRule);
        }
        if (field != null) {
            relational.addProp("field", field);
        }
        return relational;
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
