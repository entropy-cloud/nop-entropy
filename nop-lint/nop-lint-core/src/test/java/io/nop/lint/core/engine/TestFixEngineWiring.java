package io.nop.lint.core.engine;

import io.nop.core.model.object.DynamicObject;
import io.nop.lint.core.NopLintException;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.lang.TreeSitterLanguageAdapter;
import io.nop.lint.core.node.LintTree;
import io.nop.lint.core.rule.RuleDslModel;
import io.nop.lint.core.rule.RuleDslParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The engine wiring of the autofix surface (roadmap item 25 Phase 1, plan
 * 2026-09-22-2225-1): a rule with a template fix renders one concrete
 * rewrite per match and the fix rides the diagnostic; suggestion-only
 * templates report without a fix (never applied); fix+xscript is a
 * parse-time rejection and fix on the XML path a compile-time rejection —
 * both fail-closed instead of a silently dead template.
 */
public class TestFixEngineWiring {

    private static final LintLanguage JAVA = new TreeSitterLanguageAdapter("java",
            io.nop.treesitter.language.Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin"),
            null);

    private final RuleDslParser parser = new RuleDslParser();

    @Test
    public void diagnosticsCarryRenderedFixes() {
        RuleDslModel parsed = parser.parseRuleModel(fixModel("demo/fix-render",
                "Replace with safe log", "log($$$ARGS);", false,
                "System.out.println($$$ARGS)"));

        LintTree tree = JAVA.parse("class Demo { void m() { System.out.println(\"x\"); } }");
        CompiledRule compiled = CompiledRule.compile(parsed, JAVA);
        List<Diagnostic> diagnostics = RuleSetRunner.run(List.of(compiled), tree,
                LintStats.builder(), LintProfile.STANDARD, FileBudget.withoutLimits(), null, false);

        assertEquals(1, diagnostics.size());
        assertNotNull(diagnostics.get(0).fix(), "an applied fix rides the diagnostic");
        assertEquals("log(\"x\");", diagnostics.get(0).fix().replacement(),
                "the template rendered the captured argument slice");
        assertEquals("Replace with safe log", diagnostics.get(0).fix().description());
        assertEquals(0, diagnostics.get(0).fix().order(),
                "the generation ordinal starts at the first rule/match");
    }

    @Test
    public void suggestOnlyRulesReportWithoutFix() {
        RuleDslModel parsed = parser.parseRuleModel(fixModel("demo/fix-suggest",
                "Consider the safe log", "log($$$ARGS);", true,
                "System.out.println($$$ARGS)"));

        LintTree tree = JAVA.parse("class Demo { void m() { System.out.println(\"x\"); } }");
        CompiledRule compiled = CompiledRule.compile(parsed, JAVA);
        List<Diagnostic> diagnostics = RuleSetRunner.run(List.of(compiled), tree,
                LintStats.builder(), LintProfile.STANDARD, FileBudget.withoutLimits(), null, false);

        assertEquals(1, diagnostics.size());
        assertNull(diagnostics.get(0).fix(), "a suggestion-only fix never reaches the applier");
    }

    @Test
    public void fixPlusXscriptRejectedAtParseTime() {
        DynamicObject model = fixModel("demo/fix-xscript", "d", "x", false);
        model.addProp("xscript", "report({});");

        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(model));
        assertTrue(ex.getMessage().contains("demo/fix-xscript"));
        assertTrue(ex.getMessage().contains("'xscript'"), ex.getMessage());
    }

    @Test
    public void fixWithoutTemplateRejected() {
        DynamicObject model = fixModel("demo/fix-no-template", "d", null, false);

        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(model));
        assertTrue(ex.getMessage().contains("demo/fix-no-template"));
        assertTrue(ex.getMessage().contains("'template'"), ex.getMessage());
    }

    @Test
    public void fixOnXmlLanguageRejectedAtCompileTime() {
        DynamicObject model = fixModel("demo/fix-xml", "d", "x", false);
        model.addProp("language", "XML");
        RuleDslModel parsed = parser.parseRuleModel(model);

        io.nop.lint.core.lang.LintLanguage xml = new io.nop.lint.core.xml.XmlLanguage();
        NopLintException ex = assertThrows(NopLintException.class,
                () -> CompiledRule.compile(parsed, xml));
        assertTrue(ex.getMessage().contains("demo/fix-xml"), ex.getMessage());
    }

    // ==================== helpers ====================

    private DynamicObject fixModel(String id, String description, String template, boolean suggest) {
        return fixModel(id, description, template, suggest, "foo()");
    }

    private DynamicObject fixModel(String id, String description, String template, boolean suggest,
                                   String pattern) {
        DynamicObject model = new DynamicObject("lint-rule");
        model.addProp("id", id);
        model.addProp("severity", "warning");
        model.addProp("message", "msg " + id);
        DynamicObject rule = new DynamicObject("rule");
        rule.addProp("pattern", pattern);
        model.addProp("rule", rule);
        DynamicObject fix = new DynamicObject("fix");
        fix.addProp("description", description);
        if (template != null) {
            fix.addProp("template", template);
        }
        fix.addProp("suggest", suggest);
        model.addProp("fix", fix);
        return model;
    }
}
