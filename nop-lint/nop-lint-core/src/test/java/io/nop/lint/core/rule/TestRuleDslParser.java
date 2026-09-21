package io.nop.lint.core.rule;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.model.object.DynamicObject;
import io.nop.lint.core.NopLintException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Matcher uniqueness validation and typed model build proofs for
 * {@link RuleDslParser}, plus the end-to-end proof from {@code *.rule.yml}
 * fixture paths through the registered loader chain to {@link RuleDslModel}.
 */
public class TestRuleDslParser {

    private static final String DIR = "/test/lint/rules/";

    private final RuleDslParser parser = new RuleDslParser();

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    // ==================== component level: uniqueness branches ====================

    @Test
    public void ruleLevelTwoMatchersRejected() {
        DynamicObject model = ruleModel("demo/x", b -> {
            b.addProp("pattern", "foo($x)");
            b.addProp("kind", "expression_statement");
        });

        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(model));
        assertTrue(ex.getMessage().contains("demo/x"), "message must contain the rule id: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("pattern") && ex.getMessage().contains("kind"),
                "message must name the conflicting fields: " + ex.getMessage());
    }

    @Test
    public void ruleLevelZeroMatchersRejected() {
        DynamicObject model = ruleModel("demo/empty", b -> {
        });

        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(model));
        assertTrue(ex.getMessage().contains("demo/empty"), "message must contain the rule id: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("no matcher"), "message must state the missing matcher: "
                + ex.getMessage());
    }

    @Test
    public void missingRuleContainerRejected() {
        DynamicObject model = new DynamicObject("lint-rule");
        model.addProp("id", "demo/no-container");

        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(model));
        assertTrue(ex.getMessage().contains("demo/no-container"), "message must contain the rule id");
        assertTrue(ex.getMessage().contains("rule container"), "message must name the missing container: "
                + ex.getMessage());
    }

    @Test
    public void anyBranchWithZeroMatchersRejected() {
        DynamicObject branch = new DynamicObject("matcher");
        DynamicObject model = ruleModel("demo/any-empty", b -> b.addProp("any", List.of(branch)));

        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(model));
        assertTrue(ex.getMessage().contains("demo/any-empty"), "message must contain the rule id: "
                + ex.getMessage());
        assertTrue(ex.getMessage().contains("any branch #") || ex.getMessage().contains("'any' branch"),
                "message must name the any branch: " + ex.getMessage());
    }

    @Test
    public void anyBranchConjunctionIsLegal() {
        DynamicObject branch = new DynamicObject("matcher");
        branch.addProp("pattern", "bar($x, $y)");
        branch.addProp("kind", "call_expression");
        DynamicObject model = ruleModel("demo/any-conjunction", b -> b.addProp("any", List.of(branch)));

        RuleDslModel parsed = parser.parseRuleModel(model);

        RuleDslModel.Matcher matcher = parsed.getMatcher();
        assertNotNull(matcher.getAny(), "any matcher must be kept");
        assertEquals(1, matcher.getAny().size());
        RuleDslModel.Branch parsedBranch = matcher.getAny().get(0);
        assertEquals("bar($x, $y)", parsedBranch.getPattern());
        assertEquals("call_expression", parsedBranch.getKind(), "conjunction fields must both survive");
        assertNull(matcher.getPattern());
        assertNull(parsed.getMatcher().getKind());
    }

    @Test
    public void emptyAnyMatcherRejected() {
        DynamicObject model = ruleModel("demo/any-no-branches", b -> b.addProp("any", List.of()));

        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(model));
        assertTrue(ex.getMessage().contains("demo/any-no-branches"), "message must contain the rule id");
        assertTrue(ex.getMessage().contains("without branches"), "message must state the empty any: "
                + ex.getMessage());
    }

    @Test
    public void nonDynamicModelRejected() {
        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel("not-a-model"));
        assertTrue(ex.getMessage().contains("Unsupported lint rule model type"),
                "message must state the unsupported model type: " + ex.getMessage());
    }

    // ==================== end to end: fixture path -> typed model ====================

    @Test
    public void endToEndFromAnyFixtureToTypedMatcherStructure() {
        RuleDslModel model = parser.loadRuleModel(DIR + "valid-any.rule.yml");

        assertEquals("demo/no-dynamic-dispatch", model.getId());
        RuleDslModel.Matcher matcher = model.getMatcher();
        assertNotNull(matcher.getAny(), "any matcher must be kept");
        assertNull(matcher.getPattern(), "single-text matcher slot must stay empty for any rules");
        assertEquals(2, matcher.getAny().size(), "any branch count must match the YAML");

        RuleDslModel.Branch first = matcher.getAny().get(0);
        assertEquals("$obj.invoke($name)", first.getPattern());
        assertNull(first.getKind());

        RuleDslModel.Branch second = matcher.getAny().get(1);
        assertEquals("$obj.getClass().getMethod($name)", second.getPattern());
        assertEquals("method_invocation", second.getKind());
    }

    @Test
    public void endToEndFullFieldRoundTrip() {
        RuleDslModel model = parser.loadRuleModel(DIR + "valid-full.rule.yml");

        assertEquals("demo/no-file-stream", model.getId());
        assertEquals("Java", model.getLanguage());
        assertEquals("warning", model.getSeverity());
        assertEquals("Prefer NIO file APIs over java.io streams", model.getMessage());
        assertEquals("new FileInputStream($f)", model.getMatcher().getPattern());
        assertNull(model.getMatcher().getAny());
        assertEquals("captures.f", model.getXscript());
        assertEquals(250, model.getXscriptTimeoutMs());
        assertEquals(Set.of("L1", "L2"), model.getRequires());
        assertEquals(Map.of("maxFiles", "16"), model.getOptions());
        assertEquals(Map.of("mode", "strict"), model.getSettings());

        RuleDslModel.Metadata metadata = model.getMetadata();
        assertNotNull(metadata);
        assertEquals("api-usage", metadata.getCategory());
        assertEquals("warning", metadata.getSeverity());
        assertEquals(Boolean.FALSE, metadata.isAutoFixable());
        assertEquals("1.2", metadata.getVersion());
        assertEquals(List.of("eslint", "custom"), metadata.getSource());

        RuleDslModel.Files files = model.getFiles();
        assertNotNull(files);
        assertEquals(List.of("src/main/**/*.java"), files.getInclude());
        assertEquals(List.of("**/*Test.java", "**/*IT.java"), files.getExclude());
    }

    @Test
    public void endToEndSimplePatternMatcher() {
        RuleDslModel model = parser.loadRuleModel(DIR + "valid-simple.rule.yml");

        assertEquals("demo/no-console", model.getId());
        assertEquals("System.out.println($arg)", model.getMatcher().getPattern());
        assertNull(model.getMatcher().getKind());
        assertNull(model.getMatcher().getRegex());
        assertNull(model.getMatcher().getAny());
        assertEquals(100, model.getXscriptTimeoutMs(), "timeout default must be applied");
    }

    @Test
    public void endToEndZeroMatcherFixtureThrowsNopLintException() {
        NopLintException ex = assertThrows(NopLintException.class,
                () -> parser.loadRuleModel(DIR + "invalid-no-matcher.rule.yml"),
                "rule container with zero matchers must fail closed");
        assertTrue(ex.getMessage().contains("demo/no-matcher"), "message must contain the rule id: "
                + ex.getMessage());
    }

    @Test
    public void endToEndTwoMatcherFixtureThrowsNopLintException() {
        NopLintException ex = assertThrows(NopLintException.class,
                () -> parser.loadRuleModel(DIR + "invalid-two-matchers.rule.yml"),
                "rule container with two matchers must fail closed");
        assertTrue(ex.getMessage().contains("demo/two-matchers"), "message must contain the rule id: "
                + ex.getMessage());
        assertTrue(ex.getMessage().contains("pattern") && ex.getMessage().contains("kind"),
                "message must name the conflicting fields: " + ex.getMessage());
    }

    @Test
    public void endToEndEmptyAnyBranchFixtureThrowsNopLintException() {
        NopLintException ex = assertThrows(NopLintException.class,
                () -> parser.loadRuleModel(DIR + "invalid-any-empty-branch.rule.yml"),
                "any branch with zero matchers must fail closed");
        assertTrue(ex.getMessage().contains("demo/any-empty-branch"), "message must contain the rule id: "
                + ex.getMessage());
    }

    // ==================== helpers ====================

    private DynamicObject ruleModel(String id, java.util.function.Consumer<DynamicObject> ruleConfigurer) {
        DynamicObject model = new DynamicObject("lint-rule");
        model.addProp("id", id);
        DynamicObject rule = new DynamicObject("rule");
        ruleConfigurer.accept(rule);
        model.addProp("rule", rule);
        return model;
    }
}
