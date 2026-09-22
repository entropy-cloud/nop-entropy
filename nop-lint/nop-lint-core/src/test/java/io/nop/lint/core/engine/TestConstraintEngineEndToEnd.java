package io.nop.lint.core.engine;

import io.nop.core.model.object.DynamicObject;
import io.nop.lint.core.NopLintException;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.lang.TreeSitterLanguageAdapter;
import io.nop.lint.core.rule.RuleDslModel;
import io.nop.lint.core.rule.RuleDslParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end and wiring proofs for the constraint pipeline (roadmap item 22,
 * plan 2026-09-22-1045-3): a constrained rule flows load → compile → match →
 * constraint evaluation → diagnostics through the real engine, the evaluator
 * is verifiably invoked at run time (flipping one constraint flips the
 * outcome and the counter — Minimum Rules #23), every evaluable constraint
 * has an engine-level hit/miss leg (Minimum Rules #25), the typeOf × L2 gate
 * surfaces as skippedByProfile (load success, never a load failure), and
 * constraint filtering sits before xscript per design 03 §1.1.
 */
public class TestConstraintEngineEndToEnd {

    private static final LintLanguage JAVA = new TreeSitterLanguageAdapter("java",
            io.nop.treesitter.language.Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin"),
            null);

    private final RuleDslParser parser = new RuleDslParser();

    private final LanguageRegistry registry = LanguageRegistry.empty();

    public TestConstraintEngineEndToEnd() {
        registry.register(JAVA);
    }

    // ==================== wiring: the evaluator is really called ====================

    @Test
    public void constraintFlipChangesOutcomeAndCounter() {
        String source = "class Demo { boolean m(int x, int y) { return x == y; } }";

        // No constraints: the match reports.
        LintResult unconstrained = lint(ruleModel("demo/flip", rule -> {
        }), source);
        assertEquals(1, unconstrained.diagnostics().size(),
                "without constraints the match must produce a diagnostic");

        // sameText on a differing pair: the same match is filtered and counted.
        LintResult filtered = lint(ruleModel("demo/flip", rule -> {
        }, model -> model.addProp("constraints", List.of(
                constraintElement("sameText", e -> e.addProp("captures", List.of("$A", "$B")))))), source);
        assertEquals(0, filtered.diagnostics().size(),
                "a failed constraint must remove the diagnostic");
        assertEquals(1, filtered.stats().getConstraintFilteredMatches(),
                "the removed match must be observable in the stats counter");

        // The same constraint on an identical pair: the evaluator lets it through —
        // proof the filter is real evaluation, not an unconditional block.
        LintResult passing = lint(ruleModel("demo/flip", rule -> {
        }, model -> model.addProp("constraints", List.of(
                constraintElement("sameText", e -> e.addProp("captures", List.of("$A", "$B")))))),
                "class Demo { boolean m(int x) { return x == x; } }");
        assertEquals(1, passing.diagnostics().size(),
                "a holding constraint must let the diagnostic through");
        assertEquals(0, passing.stats().getConstraintFilteredMatches());
    }

    // ==================== per-constraint engine matrix (Minimum Rules #25) ====================

    @Test
    public void differentTextFiltersIdenticalOperands() {
        LintResult result = lint(ruleModel("demo/diff-text", rule -> {
        }, model -> model.addProp("constraints", List.of(
                constraintElement("differentText", e -> e.addProp("captures", List.of("$A", "$B")))))),
                "class Demo { boolean m(int x) { return x == x; } }");
        assertEquals(0, result.diagnostics().size());
        assertEquals(1, result.stats().getConstraintFilteredMatches());
    }

    @Test
    public void regexConstraintHitsAndMisses() {
        Consumer<DynamicObject> regex = model -> model.addProp("constraints", List.of(
                constraintElement("regex", e -> {
                    e.addProp("capture", "$NAME");
                    e.addProp("pattern", "^[a-z]+$");
                })));

        LintResult hit = lint(ruleModel("demo/regex-c", rule -> rule.prop_set("pattern",
                "int $NAME = $VAL;"), regex), "class Demo { void m() { int value = 1; } }");
        assertEquals(1, hit.diagnostics().size());

        LintResult miss = lint(ruleModel("demo/regex-c", rule -> rule.prop_set("pattern",
                "int $NAME = $VAL;"), regex), "class Demo { void m() { int Value = 1; } }");
        assertEquals(0, miss.diagnostics().size());
        assertEquals(1, miss.stats().getConstraintFilteredMatches());
    }

    @Test
    public void inListConstraintHitsAndMisses() {
        Consumer<DynamicObject> inList = model -> model.addProp("constraints", List.of(
                constraintElement("inList", e -> {
                    e.addProp("capture", "$M");
                    e.addProp("values", List.of("get", "post"));
                })));

        LintResult hit = lint(ruleModel("demo/in-list-c", rule -> rule.prop_set("pattern", "mode($M)"),
                inList), "class Demo { void m() { mode(get); } }");
        assertEquals(1, hit.diagnostics().size());

        LintResult miss = lint(ruleModel("demo/in-list-c", rule -> rule.prop_set("pattern", "mode($M)"),
                inList), "class Demo { void m() { mode(patch); } }");
        assertEquals(0, miss.diagnostics().size());
        assertEquals(1, miss.stats().getConstraintFilteredMatches());
    }

    @Test
    public void notExistsFiltersWhenSubtreeContainsPattern() {
        Consumer<DynamicObject> notExists = model -> model.addProp("constraints", List.of(
                constraintElement("notExists", e -> e.addProp("pattern", "return null;"))));

        LintResult hit = lint(ruleModel("demo/not-exists-c", rule -> rule.prop_set("pattern",
                "if ($COND) return $VAL;"), notExists),
                "class Demo { Object m(boolean c) { if (c) return 1; return null; } }");
        assertEquals(1, hit.diagnostics().size(),
                "the if subtree has no 'return null' — the match must be reported");

        LintResult miss = lint(ruleModel("demo/not-exists-c", rule -> rule.prop_set("pattern",
                "if ($COND) return $VAL;"), notExists),
                "class Demo { Object m(boolean c) { if (c) return null; return null; } }");
        assertEquals(0, miss.diagnostics().size(),
                "the if subtree contains 'return null' — the match must be filtered");
        assertEquals(1, miss.stats().getConstraintFilteredMatches());
    }

    @Test
    public void withinDepthFiltersDeepSubtrees() {
        Consumer<DynamicObject> withinDepth = model -> model.addProp("constraints", List.of(
                constraintElement("withinDepth", e -> e.addProp("max", 2))));

        LintResult hit = lint(ruleModel("demo/depth-c", rule -> rule.prop_set("pattern", "return $V;"),
                withinDepth), "class Demo { int m(int x) { return x; } }");
        assertEquals(1, hit.diagnostics().size(), "a shallow return (depth 1) must be reported");

        LintResult miss = lint(ruleModel("demo/depth-c", rule -> rule.prop_set("pattern", "return $V;"),
                withinDepth), "class Demo { int m(int a) { return a.b().c(); } }");
        assertEquals(0, miss.diagnostics().size(),
                "a deep return (depth 4) must be filtered under the uniform polarity");
        assertEquals(1, miss.stats().getConstraintFilteredMatches());
    }

    // ==================== compile-time capture validation ====================

    @Test
    public void undeclaredCaptureRejectedAtCompileTime() {
        RuleDslModel model = parser.parseRuleModel(constrainedModel("demo/bad-capture",
                constraintElement("sameText", e -> e.addProp("captures", List.of("$A", "$GHOST")))));

        NopLintException ex = assertThrows(NopLintException.class,
                () -> CompiledRule.compile(model, JAVA),
                "a constraint capture the matcher never declares must fail at compile time");
        assertTrue(ex.getMessage().contains("demo/bad-capture"),
                "message must contain the rule id: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("sameText"),
                "message must name the constraint kind: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("GHOST"),
                "message must name the undeclared capture: " + ex.getMessage());
    }

    @Test
    public void sequenceCaptureReferenceRejectedAtCompileTime() {
        RuleDslModel model = parser.parseRuleModel(constrainedModel("demo/seq-ref",
                "demo($$$ARGS)",
                constraintElement("inList", e -> {
                    e.addProp("capture", "$ARGS");
                    e.addProp("values", List.of("x"));
                })));

        NopLintException ex = assertThrows(NopLintException.class,
                () -> CompiledRule.compile(model, JAVA));
        assertTrue(ex.getMessage().contains("demo/seq-ref"));
        assertTrue(ex.getMessage().contains("ARGS"));
        assertTrue(ex.getMessage().contains("sequence capture"),
                "sequence captures must be rejected with the precise reason: " + ex.getMessage());
    }

    // ==================== typeOf × L2 gate: skip, never load failure ====================

    @Test
    public void typeOfRuleLoadsThenSkipsByProfile() {
        // Parse succeeds — the parser gate is satisfied by requires: "L2".
        RuleDslModel model = parser.parseRuleModel(ruleModel("demo/typeof-skip",
                rule -> rule.prop_set("pattern", "$X.foo()"),
                top -> {
                    top.addProp("requires", "L2");
                    top.addProp("constraints", List.of(
                            constraintElement("typeOf", e -> {
                                e.addProp("capture", "$X");
                                e.addProp("is", "String");
                            })));
                }));

        LintResult result = lint(model, "class Demo { int m(String s) { return s.foo(); } }");
        assertTrue(result.diagnostics().isEmpty(),
                "a typeOf rule must never report in a profile without L2");
        assertEquals(1, result.stats().getRulesSkippedByProfile(),
                "the whole rule must be counted as profile-skipped (never L1-faked)");
        assertTrue(result.stats().getSkippedRuleIds().contains("demo/typeof-skip"),
                "the skipped id is part of the stats contract");
        assertEquals(0, result.stats().getRulesExecuted(),
                "a profile-skipped rule must not consume the executed count");
        assertEquals(0, result.stats().getConstraintFilteredMatches());
    }

    // ==================== pipeline position: constraints before xscript ====================

    @Test
    public void constraintFilterRunsBeforeXscript() {
        // xscript is a top-level field (design 01 §2 schema convention), like
        // constraints — both ride the model root, never the rule container.
        Consumer<DynamicObject> constrainedWithXscript = top -> {
            top.addProp("xscript", "captures.A");
            top.addProp("constraints", List.of(
                    constraintElement("sameText", e -> e.addProp("captures", List.of("$A", "$B")))));
        };
        DynamicObject filteredAll = ruleModel("demo/xscript-order", rule -> {
        }, constrainedWithXscript);
        LintResult result = lint(parser.parseRuleModel(filteredAll),
                "class Demo { boolean m(int x, int y) { return x == y; } }");

        assertEquals(0, result.diagnostics().size());
        assertEquals(1, result.stats().getConstraintFilteredMatches());
        assertEquals(0, result.stats().getXscriptMatchesExecuted(),
                "a constraint-filtered match never reaches its xscript body (design 03 §1.1 order)");

        DynamicObject allPass = ruleModel("demo/xscript-order", rule -> {
        }, constrainedWithXscript);
        LintResult passing = lint(parser.parseRuleModel(allPass),
                "class Demo { boolean m(int x) { return x == x; } }");
        assertEquals(1, passing.stats().getXscriptMatchesExecuted(),
                "a holding constraint lets the match reach the xscript body");
    }

    // ==================== helpers ====================

    private LintResult lint(DynamicObject model, String source) {
        return lint(parser.parseRuleModel(model), source);
    }

    private LintResult lint(RuleDslModel rule, String source) {
        LintEngine engine = new LintEngine(registry, LintProfile.STANDARD);
        return engine.lint(List.of(rule), JAVA, source);
    }

    private DynamicObject ruleModel(String id, Consumer<DynamicObject> ruleConfigurer) {
        return ruleModel(id, ruleConfigurer, null);
    }

    private DynamicObject ruleModel(String id, Consumer<DynamicObject> ruleConfigurer,
                                    Consumer<DynamicObject> modelConfigurer) {
        DynamicObject model = new DynamicObject("lint-rule");
        model.addProp("id", id);
        model.addProp("severity", "warning");
        model.addProp("message", "msg " + id);
        DynamicObject rule = new DynamicObject("rule");
        rule.addProp("pattern", "$A == $B");
        ruleConfigurer.accept(rule);
        model.addProp("rule", rule);
        if (modelConfigurer != null)
            modelConfigurer.accept(model);
        return model;
    }

    /**
     * A constrained rule whose pattern declares only the $A capture — used by
     * the compile-time capture-validation tests.
     */
    private DynamicObject constrainedModel(String id, DynamicObject... constraints) {
        return ruleModel(id, rule -> {
        }, model -> model.addProp("constraints", List.of(constraints)));
    }

    private DynamicObject constrainedModel(String id, String pattern, DynamicObject constraint) {
        DynamicObject model = ruleModel(id, rule -> rule.prop_set("pattern", pattern));
        model.addProp("constraints", List.of(constraint));
        return model;
    }

    private DynamicObject constraintElement(String kind, Consumer<DynamicObject> bodyConfigurer) {
        DynamicObject element = new DynamicObject("constraint");
        DynamicObject body = new DynamicObject(kind);
        bodyConfigurer.accept(body);
        element.addProp(kind, body);
        return element;
    }
}
