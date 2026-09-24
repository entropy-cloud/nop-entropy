package io.nop.lint.core.engine;

import io.nop.core.model.object.DynamicObject;
import io.nop.lint.core.NopLintException;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.lang.TreeSitterLanguageAdapter;
import io.nop.lint.core.rule.RuleDslModel;
import io.nop.lint.core.rule.RuleDslParser;
import io.nop.lint.core.semantic.ScopeResolver;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code scope} binding gate matrix (roadmap item 33 Phase 2, plan
 * Decisions 2/6/7): a {@code requires: SCOPE} rule reaches its script's
 * scope queries only through a live resolver on a named file under the deep
 * profile — every other combination lands in exactly one observable exit,
 * and an undeclared reference fails at compile time.
 */
public class TestScopeBinding {

    private static final LintLanguage JAVA = new TreeSitterLanguageAdapter("java",
            io.nop.treesitter.language.Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin"),
            null);

    private static final String SOURCE = "class Demo {\n"
            + "  int m() {\n"
            + "    int value = 1;\n"
            + "    return value;\n"
            + "  }\n"
            + "}\n";

    private final RuleDslParser parser = new RuleDslParser();
    private final LanguageRegistry registry = LanguageRegistry.empty();
    private final FakeScopeResolver resolver = new FakeScopeResolver();

    public TestScopeBinding() {
        registry.register(JAVA);
    }

    @Test
    public void liveResolverOnNamedFileServesTheBinding() {
        resolver.available = true;
        resolver.shadows = true;

        LintResult result = lint(LintProfile.DEEP, scopeRule(), "demo/S.java");

        assertEquals(1, result.stats().getRulesExecuted());
        assertEquals(1, result.diagnostics().size(), "the shadow judgment reports");
        assertTrue(result.diagnostics().get(0).message().contains("shadows"),
                "the binding handed the resolver's answer to the script");
        assertEquals(0, result.stats().getRulesDegraded());
    }

    @Test
    public void missingResolverDegradesBeforeCompiling() {
        resolver.available = false;

        LintResult result = lint(LintProfile.DEEP, scopeRule(), "demo/S.java");

        assertEquals(1, result.stats().getRulesDegraded());
        assertEquals(0, result.stats().getRulesExecuted());
        assertEquals(0, result.diagnostics().size());
    }

    @Test
    public void unnamedRunDegrades() {
        resolver.available = true;

        LintEngine engine = new LintEngine(registry, LintProfile.DEEP, new DeepResolvers(null, resolver, null, null));
        LintResult result = engine.lint(List.of(scopeRule()), JAVA, SOURCE);

        assertEquals(1, result.stats().getRulesDegraded(),
                "a position-keyed query needs a named file");
        assertEquals(0, result.diagnostics().size());
    }

    @Test
    public void fastAndStandardCeilingsSkipScopeRules() {
        resolver.available = true;
        for (LintProfile profile : List.of(LintProfile.FAST, LintProfile.STANDARD)) {
            LintResult result = lint(profile, scopeRule(), "demo/S.java");
            assertEquals(1, result.stats().getRulesSkippedByProfile(), profile + " skips by ceiling");
        }
    }

    @Test
    public void undeclaredReferenceFailsCompilation() {
        DynamicObject model = new DynamicObject("lint-rule");
        model.addProp("id", "demo/undeclared-scope");
        model.addProp("severity", "warning");
        model.addProp("message", "msg");
        DynamicObject rule = new DynamicObject("rule");
        rule.addProp("kind", "variable_declarator");
        model.addProp("rule", rule);
        model.addProp("xscript", "if (scope.shadows(node)) { report({ message: 'x' }); }");
        RuleDslModel undeclared = parser.parseRuleModel(model);

        resolver.available = true;
        LintEngine engine = new LintEngine(registry, LintProfile.DEEP, new DeepResolvers(null, resolver, null, null));

        NopLintException ex = assertThrows(NopLintException.class,
                () -> engine.lint(List.of(undeclared), JAVA, "demo/S.java", SOURCE));
        assertTrue(ex.getMessage().contains("demo/undeclared-scope"), ex.getMessage());
    }

    @Test
    public void definitionBindingReturnsNavigableNode() {
        resolver.available = true;
        // resolve the `value` use (line 4) to its declaration (line 3):
        // the binding maps the resolver's byte answer back to a CST node
        DynamicObject model = new DynamicObject("lint-rule");
        model.addProp("id", "demo/scope-definition");
        model.addProp("severity", "warning");
        model.addProp("message", "msg");
        DynamicObject rule = new DynamicObject("rule");
        rule.addProp("pattern", "return $V");
        model.addProp("rule", rule);
        model.addProp("requires", "SCOPE");
        model.addProp("xscript", """
                let def = scope.definition(captures.V);
                if (def != null) {
                    report({ message: 'def-kind=' + scope.kind(def) });
                }""");
        RuleDslModel defRule = parser.parseRuleModel(model);

        resolver.definitionByte = 0;
        resolver.liveByte = true;
        LintEngine engine = new LintEngine(registry, LintProfile.DEEP, new DeepResolvers(null, resolver, null, null));
        LintResult result = engine.lint(List.of(defRule), JAVA, "demo/S.java", SOURCE);

        assertEquals(1, result.diagnostics().size(),
                "the definition answer mapped back to a real CST node");
        assertTrue(result.diagnostics().get(0).message().contains("def-kind="),
                "the returned node answers navigation queries");
    }

    private LintResult lint(LintProfile profile, RuleDslModel rule, String filePath) {
        LintEngine engine = new LintEngine(registry, profile, new DeepResolvers(null, resolver, null, null));
        return engine.lint(List.of(rule), JAVA, filePath, SOURCE);
    }

    private RuleDslModel scopeRule() {
        DynamicObject model = new DynamicObject("lint-rule");
        model.addProp("id", "demo/scope-shadow");
        model.addProp("severity", "warning");
        model.addProp("message", "msg");
        DynamicObject rule = new DynamicObject("rule");
        rule.addProp("kind", "variable_declarator");
        model.addProp("rule", rule);
        model.addProp("requires", "SCOPE");
        model.addProp("xscript",
                "if (scope.shadows(node)) { report({ message: 'shadows!' }); }");
        return parser.parseRuleModel(model);
    }

    /**
     * Deterministic resolver: fixed answers, no file IO — the gate and the
     * binding behavior are what these tests pin, not the Java parsing.
     */
    private static final class FakeScopeResolver implements ScopeResolver {

        boolean available;
        boolean shadows;
        boolean liveByte;
        long definitionByte;

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public long definitionOf(String filePath, int line, int col) {
            return liveByte ? definitionByte : -1;
        }

        @Override
        public List<String> declaredNames(String filePath, int line, int col) {
            return List.of();
        }

        @Override
        public String scopeKind(String filePath, int line, int col) {
            return "block";
        }

        @Override
        public boolean shadows(String filePath, int line, int col) {
            return shadows;
        }
    }
}
