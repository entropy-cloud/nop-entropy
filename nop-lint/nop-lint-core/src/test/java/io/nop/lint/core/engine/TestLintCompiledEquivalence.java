package io.nop.lint.core.engine;

import io.nop.lint.core.engine.LanguageRegistry;
import io.nop.lint.core.engine.LintProfile;
import io.nop.lint.core.engine.LintResult;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.lint.core.rule.RuleDslModel;
import io.nop.lint.core.testing.JavaBindingTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The precompiled entry's observable equivalence with the per-model path
 * (plan 10 Phase 2): the gate matrix reads identically on both faces, one
 * compiled rule set serves different files without cross-file bleed, and the
 * byte[] face produces exactly the String face's diagnostics.
 */
class TestLintCompiledEquivalence {

    private static final String VIOLATING = "class Warn {\n    void x() {\n"
            + "        System.out.println(\"w\");\n    }\n}\n";
    private static final String OTHER = "class Other {\n    void y() {\n"
            + "        throw new RuntimeException(\"o\");\n    }\n}\n";

    private static LanguageRegistry registry;
    private static LintEngine engine;
    private static List<CompiledRule> compiled;
    private static List<RuleDslModel> models;

    @BeforeAll
    static void setup() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
        registry = JavaBindingTestSupport.registryWithJava();
        engine = new LintEngine(registry, LintProfile.STANDARD);
        models = new java.util.ArrayList<>();
        new io.nop.lint.core.cli.RuleSetLoader().loadRuleSet("/test/lint/cli-rules")
                .rulesByLanguage().values().forEach(models::addAll);
        io.nop.lint.core.lang.LintLanguage java = registry.resolve("java");
        compiled = models.stream().map(m -> CompiledRule.compile(m, java)).toList();
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private static RuleDslModel patternModel(String id, String pattern) {
        var model = new io.nop.core.model.object.DynamicObject("lint-rule");
        model.addProp("id", id);
        model.addProp("language", "Java");
        model.addProp("severity", "warning");
        model.addProp("message", "msg " + id);
        var rule = new io.nop.core.model.object.DynamicObject("rule");
        rule.addProp("pattern", pattern);
        model.addProp("rule", rule);
        return new io.nop.lint.core.rule.RuleDslParser().parseRuleModel(model);
    }

    @Test
    void precompiledPathMatchesThePerModelPathDiagnosticForDiagnostic() {
        LintResult perModel = engine.lint(models, "java", "eq/A.java", VIOLATING);
        LintResult precompiled = engine.lintCompiled(compiled, "java", "eq/A.java", VIOLATING);

        assertEquals(perModel.diagnostics().size(), precompiled.diagnostics().size());
        for (int i = 0; i < perModel.diagnostics().size(); i++) {
            Diagnostic expected = perModel.diagnostics().get(i);
            Diagnostic actual = precompiled.diagnostics().get(i);
            assertEquals(expected.ruleId(), actual.ruleId());
            assertEquals(expected.severity(), actual.severity());
            assertEquals(expected.range(), actual.range());
        }
    }

    @Test
    void oneCompiledRuleSetServesDifferentFilesWithoutBleed() {
        LintResult first = engine.lintCompiled(compiled, "java", "eq/first.java", VIOLATING);
        LintResult second = engine.lintCompiled(compiled, "java", "eq/second.java", OTHER);

        assertTrue(first.diagnostics().stream().allMatch(d -> d.ruleId().equals("demo/no-print")),
                "file one only trips the no-print rule: " + first.diagnostics());
        assertTrue(second.diagnostics().stream()
                        .allMatch(d -> d.ruleId().equals("demo/no-bare-throw")),
                "file two only trips the raw-throw rule: " + second.diagnostics());
    }

    @Test
    void bytesFaceEqualsStringFace() {
        LintResult fromString = engine.lintCompiled(compiled, "java", "eq/b.java", VIOLATING);
        LintResult fromBytes = engine.lintCompiled(compiled, "java", "eq/b.java",
                VIOLATING.getBytes(StandardCharsets.UTF_8));

        assertEquals(fromString.diagnostics().size(), fromBytes.diagnostics().size());
        for (int i = 0; i < fromString.diagnostics().size(); i++) {
            assertEquals(fromString.diagnostics().get(i).range(),
                    fromBytes.diagnostics().get(i).range());
            assertEquals(fromString.diagnostics().get(i).message(),
                    fromBytes.diagnostics().get(i).message());
        }
    }

    @Test
    void unknownRequiresTokenSkipsOnBothPaths() {
        RuleDslModel unknown = patternModel("demo/eq-unknown", "System.out.println($$$A)");
        unknown.getRequires().add("bogus-capability");

        io.nop.lint.core.lang.LintLanguage java = registry.resolve("java");
        CompiledRule compiledUnknown = CompiledRule.compile(unknown, java);

        LintResult perModel = engine.lint(List.of(unknown), "java", "eq/u.java", VIOLATING);
        LintResult precompiled = engine.lintCompiled(List.of(compiledUnknown), "java",
                "eq/u.java", VIOLATING);

        assertEquals(1, perModel.stats().getRulesSkippedByProfile());
        assertEquals(1, precompiled.stats().getRulesSkippedByProfile(),
                "the raw unknown token reads as skipped on the precompiled path too");
        assertEquals(0, precompiled.stats().getRulesExecuted());
        assertEquals(0, precompiled.diagnostics().size());
    }
}
