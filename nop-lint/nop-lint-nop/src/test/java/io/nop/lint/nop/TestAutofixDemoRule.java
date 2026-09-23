package io.nop.lint.nop;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.lint.core.engine.Diagnostic;
import io.nop.lint.core.engine.LanguageRegistry;
import io.nop.lint.core.engine.LintEngine;
import io.nop.lint.core.engine.LintProfile;
import io.nop.lint.core.engine.LintResult;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.rule.RuleDslModel;
import io.nop.lint.core.rule.RuleDslParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The engine-level autofix proof of the demo fix rule (roadmap item 25,
 * plan 2026-09-22-2225-1 Phase 3 adjudication): the fixture-local
 * {@code demo/no-print-demo} rule loads through the real rule pipeline with
 * its fix surface intact, and one real engine run renders the concrete
 * rewrite — the RuleTester {@code .expect} format deliberately has no fix
 * field (plan 2137-1 contract), so the fix联动 is proven here on the real
 * loaded rule through the public engine surface.
 */
public class TestAutofixDemoRule {

    private static final String DEMO_RULE_PATH =
            "/test/lint/suites/autofix/no-print-demo/no-print-demo.rule.yml";

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void demoRuleLoadsThroughTheRegisteredPipelineWithFixSurface() {
        RuleDslModel rule = new RuleDslParser().loadRuleModel(DEMO_RULE_PATH);

        assertEquals("demo/no-print-demo", rule.getId());
        assertNotNull(rule.getFix(), "the demo rule carries its fix declaration");
        assertEquals("Replace with the log call", rule.getFix().getDescription());
        assertEquals("log($$$ARGS)", rule.getFix().getTemplate());
        assertFalse(rule.getFix().isSuggest(), "the demo fix is applicable, not suggestion-only");
        assertTrue(rule.getMetadata().isAutoFixable());
    }

    @Test
    public void realEngineRunRendersTheTemplateRewrite() {
        RuleDslModel model = new RuleDslParser().loadRuleModel(DEMO_RULE_PATH);
        LintLanguage java = LanguageRegistry.discoverDefaults().resolve("java");

        LintEngine engine = new LintEngine(LanguageRegistry.discoverDefaults(), LintProfile.STANDARD);
        LintResult result = engine.lint(List.of(model), java,
                "class Demo { void m() { System.out.println(\"x\"); } }");

        assertEquals(1, result.diagnostics().size());
        Diagnostic diagnostic = result.diagnostics().get(0);
        assertNotNull(diagnostic.fix(), "the applied fix rides the diagnostic");
        assertEquals("log(\"x\")", diagnostic.fix().replacement());
        assertTrue(diagnostic.fix().description().contains("log call"));
    }
}
