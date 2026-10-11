package io.nop.lint.core.engine;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.lint.core.NopLintException;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.node.LintTree;
import io.nop.lint.core.node.SourceRange;
import io.nop.lint.core.rule.RuleDslModel;
import io.nop.lint.core.rule.RuleDslParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end proofs for {@code language: XML} rules through the real engine
 * (plan Phase 2, Minimum Rules #22/#23/#25): a {@code .rule.yml} loads through
 * the registered XDSL pipeline, the rule compiles through the XML binding's
 * own compiler ({@code LintLanguage.compileRule} — the wiring Decision), the
 * XNode matcher runs inside {@code RuleSetRunner} behind the same kind filter,
 * and the diagnostics/statistics/suppression semantics are byte-identical to
 * the tree-sitter path. The kind-filtered counter is the wiring witness: a
 * rule whose tag is absent from the file is skipped by the filter (executed=0,
 * kindFiltered=1), proving the XML rule rides the shared pipeline rather than
 * a parallel one.
 */
public class TestXmlRuleEngineEndToEnd {

    private static final String DIR = "/test/lint/rules/xml/";

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    private static RuleDslModel loadRule(String fixture) {
        return new RuleDslParser().loadRuleModel(DIR + fixture);
    }

    private static LintEngine engine() {
        LanguageRegistry registry = LanguageRegistry.discoverDefaults();
        assertTrue(registry.registeredIds().contains("xml"),
                "the ServiceLoader discovery must surface the XML binding: " + registry.registeredIds());
        return new LintEngine(registry, LintProfile.STANDARD);
    }

    @Test
    public void xmlBindingIsDiscoveredThroughServiceLoader() {
        LanguageRegistry registry = LanguageRegistry.discoverDefaults();
        LintLanguage xml = registry.resolve("XML");
        assertEquals("xml", xml.id());
        assertNotNull(xml.parse("<a/>"), "the binding parses through the XNode facade");
    }

    @Test
    public void xmlRuleRunsEndToEndWithEngineCounters() {
        RuleDslModel rule = loadRule("xml-auth.rule.yml");
        assertEquals("XML", rule.getLanguage(), "the xdef enum accepts XML verbatim");

        String source = "<xbiz>\n"
                + "  <action name=\"query\">\n"
                + "    <auth>role:admin</auth>\n"
                + "  </action>\n"
                + "</xbiz>";
        LintResult result = engine().lint(List.of(rule), "xml", source);

        assertEquals(1, result.diagnostics().size());
        Diagnostic diagnostic = result.diagnostics().get(0);
        assertEquals("demo/xml-auth", diagnostic.ruleId());
        assertEquals("warning", diagnostic.severity());
        SourceRange range = diagnostic.range();
        // The auth element starts on line 3 (1-based) — the byte-offset
        // contract the console output shares.
        LineIndexOf index = new LineIndexOf(source);
        assertEquals(3, index.startLine(range));
        assertEquals(3, index.endLine(range));

        assertEquals(1, result.stats().getRulesLoaded());
        assertEquals(1, result.stats().getRulesExecuted(), "the XML rule reaches its matcher");
        assertEquals(0, result.stats().getRulesKindFiltered());
        assertEquals(1, result.stats().getDiagnostics());
        assertEquals(0, result.stats().getSuppressedDiagnostics());
    }

    @Test
    public void xmlRuleRidesTheSharedKindFilter() {
        RuleDslModel rule = loadRule("xml-auth.rule.yml");
        String source = "<other-tag>nothing to see</other-tag>";
        LintResult result = engine().lint(List.of(rule), "xml", source);

        assertTrue(result.diagnostics().isEmpty());
        assertEquals(1, result.stats().getRulesKindFiltered(),
                "the tag-disjoint rule is skipped by the shared kind index");
        assertEquals(0, result.stats().getRulesExecuted(),
                "the matcher is never invoked — filtering short-circuits matching");
    }

    @Test
    public void compositeXmlRuleRunsTheRelationalKernel() {
        RuleDslModel rule = loadRule("xml-xbiz-auth.rule.yml");
        String source = "<xbiz entity=\"NopAuthUser\">\n"
                + "  <action name=\"query\">\n"
                + "    <auth>role:admin</auth>\n"
                + "  </action>\n"
                + "</xbiz>";
        LintResult result = engine().lint(List.of(rule), "XML", source);

        assertEquals(1, result.diagnostics().size(), "all+has+inside must hold on the action");
        assertEquals("demo/xml-xbiz-auth", result.diagnostics().get(0).ruleId());
        assertEquals(1, result.stats().getRulesExecuted());
    }

    @Test
    public void compositeXmlRuleMissesWithoutTheInsideRelation() {
        RuleDslModel rule = loadRule("xml-xbiz-auth.rule.yml");
        String source = "<flow>\n"
                + "  <action name=\"query\">\n"
                + "    <auth>role:admin</auth>\n"
                + "  </action>\n"
                + "</flow>";
        LintResult result = engine().lint(List.of(rule), "XML", source);
        assertTrue(result.diagnostics().isEmpty(), "the action is not inside an xbiz element");
    }

    @Test
    public void xmlCommentSuppressionSuppressesAndCounts() {
        RuleDslModel rule = loadRule("xml-auth.rule.yml");
        String source = "<xbiz>\n"
                + "  <!-- nop-lint-disable-next-line demo/xml-auth -->\n"
                + "  <auth>role:admin</auth>\n"
                + "</xbiz>";
        LintResult result = engine().lint(List.of(rule), "xml", source);

        assertTrue(result.diagnostics().isEmpty(),
                "the directive on the preceding line suppresses the auth candidate");
        assertEquals(1, result.stats().getSuppressedDiagnostics(),
                "the removal is observable, never silent");
    }

    @Test
    public void xmlUnusedDisableDirectiveSurfaces() {
        RuleDslModel rule = loadRule("xml-auth.rule.yml");
        String source = "<xbiz>\n"
                + "  <!-- nop-lint-disable-line demo/xml-auth -->\n"
                + "  <other>clean</other>\n"
                + "</xbiz>";
        LintResult result = engine().lint(List.of(rule), "xml", source);

        assertEquals(1, result.diagnostics().size());
        assertEquals("unused-disable-directive", result.diagnostics().get(0).ruleId(),
                "the unused directive is a meta-diagnostic on the XML path too");
        assertEquals(0, result.stats().getSuppressedDiagnostics());
    }

    @Test
    public void unsupportedFormsFailClosedAtCompile() {
        LintEngine engine = engine();

        NopLintException regex = assertThrows(NopLintException.class,
                () -> engine.lint(List.of(loadRule("invalid-xml-regex.rule.yml")), "xml", "<a/>"));
        assertTrue(regex.getMessage().contains("demo/xml-invalid-regex"));

        NopLintException kind = assertThrows(NopLintException.class,
                () -> engine.lint(List.of(loadRule("invalid-xml-unknown-kind.rule.yml")), "xml", "<a/>"));
        assertTrue(kind.getMessage().contains("unknown to language"));

        NopLintException pattern = assertThrows(NopLintException.class,
                () -> engine.lint(List.of(loadRule("invalid-xml-bad-pattern.rule.yml")), "xml", "<a/>"));
        assertTrue(pattern.getMessage().contains("invalid pattern"));
    }

    @Test
    public void unparseableXmlSourceFailsClosed() {
        RuleDslModel rule = loadRule("xml-auth.rule.yml");
        assertThrows(NopLintException.class,
                () -> engine().lint(List.of(rule), "xml", "<a><b></a>"));
    }

    @Test
    public void incrementalParsingIsRejectedFailClosed() {
        LintLanguage xml = LanguageRegistry.discoverDefaults().resolve("xml");
        LintTree tree = xml.parse("<a/>");
        assertThrows(NopLintException.class, () -> xml.parseIncremental(tree, "<a/>".getBytes()));
    }

    /**
     * Line mapping mirroring the console reporter's {@code LineIndex} use, so
     * the assertions pin the same observable the CLI prints.
     */
    private static final class LineIndexOf {
        private final io.nop.lint.core.node.LineIndex index;

        LineIndexOf(String source) {
            this.index = new io.nop.lint.core.node.LineIndex(source);
        }

        int startLine(SourceRange range) {
            return index.startLine(range);
        }

        int endLine(SourceRange range) {
            return index.endLine(range);
        }
    }
}
