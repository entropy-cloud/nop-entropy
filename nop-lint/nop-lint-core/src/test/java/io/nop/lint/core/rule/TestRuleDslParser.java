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
        assertEquals("$OBJ.invoke($NAME)", first.getPattern());
        assertNull(first.getKind());

        RuleDslModel.Branch second = matcher.getAny().get(1);
        assertEquals("$OBJ.getClass().getMethod($NAME)", second.getPattern());
        assertEquals("method_invocation", second.getKind());
    }

    @Test
    public void endToEndFullFieldRoundTrip() {
        RuleDslModel model = parser.loadRuleModel(DIR + "valid-full.rule.yml");

        assertEquals("demo/no-file-stream", model.getId());
        assertEquals("Java", model.getLanguage());
        assertEquals("warning", model.getSeverity());
        assertEquals("Prefer NIO file APIs over java.io streams", model.getMessage());
        assertEquals("new FileInputStream($F)", model.getMatcher().getPattern());
        assertNull(model.getMatcher().getAny());
        assertEquals("captures.F", model.getXscript());
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
        assertEquals("System.out.println($$$ARGS)", model.getMatcher().getPattern());
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

    // ==================== xscriptTimeoutMs budget validation ====================

    @Test
    public void timeoutDefaultsTo100WhenAbsent() {
        RuleDslModel parsed = parser.parseRuleModel(patternModel("demo/t-default"));
        assertEquals(100, parsed.getXscriptTimeoutMs(), "absent xscriptTimeoutMs defaults to 100ms");
    }

    @Test
    public void timeoutAtTheCapIsAccepted() {
        RuleDslModel parsed = parser.parseRuleModel(patternModel("demo/t-cap", 1000));
        assertEquals(1000, parsed.getXscriptTimeoutMs(), "1000ms is the accepted maximum (design 07 §3)");
    }

    @Test
    public void timeoutAboveTheCapIsRejectedFailClosed() {
        NopLintException ex = assertThrows(NopLintException.class,
                () -> parser.parseRuleModel(patternModel("demo/t-over", 1001)));
        assertTrue(ex.getMessage().contains("demo/t-over"), "message must contain the rule id: "
                + ex.getMessage());
        assertTrue(ex.getMessage().contains("1001") && ex.getMessage().contains("1000"),
                "message must name the offending value and the cap: " + ex.getMessage());
    }

    @Test
    public void nonPositiveTimeoutIsRejected() {
        for (int bad : new int[]{0, -5}) {
            NopLintException ex = assertThrows(NopLintException.class,
                    () -> parser.parseRuleModel(patternModel("demo/t-nonpos", bad)),
                    "value " + bad);
            assertTrue(ex.getMessage().contains("demo/t-nonpos"), "message must contain the rule id: "
                    + ex.getMessage());
            assertTrue(ex.getMessage().contains("non-positive"), "message must state the problem: "
                    + ex.getMessage());
        }
    }

    @Test
    public void nonNumericTimeoutIsRejectedInsteadOfSilentlyDefaulting() {
        DynamicObject model = patternModel("demo/t-nonnum");
        model.addProp("xscriptTimeoutMs", "fast");

        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(model));
        assertTrue(ex.getMessage().contains("demo/t-nonnum"), "message must contain the rule id: "
                + ex.getMessage());
        assertTrue(ex.getMessage().contains("non-numeric"), "message must state the problem: "
                + ex.getMessage());
    }

    // ==================== composite + relational matchers (item 23) ====================

    @Test
    public void relationalRoundTripsWithStopByAndField() {
        RuleDslModel parsed = parser.parseRuleModel(ruleModel("demo/rel", b -> b.addProp("has",
                relational("foo($$$ARGS)", "neighbor", null, "body"))));

        RuleDslModel.Relational has = parsed.getMatcher().getHas();
        assertNotNull(has, "has matcher must be kept");
        assertEquals("foo($$$ARGS)", has.getPattern());
        assertEquals("neighbor", has.getStopBy());
        assertEquals("body", has.getField());
        assertNull(has.getStopByRule());
        assertNull(parsed.getMatcher().getPattern());
    }

    @Test
    public void relationalStopByDefaultsToEnd() {
        RuleDslModel parsed = parser.parseRuleModel(ruleModel("demo/rel-default", b -> b.addProp("inside",
                relational("bar()", null, null, null))));
        assertEquals("end", parsed.getMatcher().getInside().getStopBy(),
                "an absent stopBy must default to end (ast-grep compatible)");
    }

    @Test
    public void relationalWithoutPatternRejected() {
        DynamicObject rel = new DynamicObject("has");
        rel.addProp("stopBy", "end");
        DynamicObject model = ruleModel("demo/rel-no-pattern", b -> b.addProp("has", rel));

        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(model));
        assertTrue(ex.getMessage().contains("demo/rel-no-pattern"));
        assertTrue(ex.getMessage().contains("pattern"), "message must name the missing pattern: "
                + ex.getMessage());
    }

    @Test
    public void relationalWithUnknownStopByRejected() {
        DynamicObject model = ruleModel("demo/rel-bad-stopby", b -> b.addProp("has",
                relational("foo()", "sideways", null, null)));

        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(model));
        assertTrue(ex.getMessage().contains("demo/rel-bad-stopby"));
        assertTrue(ex.getMessage().contains("sideways"));
    }

    @Test
    public void stopByRuleWithoutStopByRuleNameRejected() {
        DynamicObject rel = new DynamicObject("has");
        rel.addProp("pattern", "foo()");
        rel.addProp("stopBy", "rule");
        DynamicObject model = ruleModel("demo/stop-by-rule-missing", b -> b.addProp("has", rel));

        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(model));
        assertTrue(ex.getMessage().contains("demo/stop-by-rule-missing"),
                "message must contain the rule id: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("stopByRule"),
                "message must name the missing stopByRule: " + ex.getMessage());
    }

    @Test
    public void stopByRuleWithoutRuleHorizonRejected() {
        DynamicObject model = ruleModel("demo/stop-by-rule-stray", b -> b.addProp("has",
                relational("foo()", "end", "my-util", null)));

        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(model));
        assertTrue(ex.getMessage().contains("demo/stop-by-rule-stray"));
        assertTrue(ex.getMessage().contains("stopByRule"));
    }

    @Test
    public void stopByRulePairingIsAccepted() {
        DynamicObject model = ruleModel("demo/stop-by-rule-ok", b -> b.addProp("has",
                relational("foo()", "rule", "my-util", null)));
        model.addProp("utils", utilsWithPattern("my-util", "bar()"));

        RuleDslModel parsed = parser.parseRuleModel(model);
        RuleDslModel.Relational has = parsed.getMatcher().getHas();
        assertEquals("rule", has.getStopBy());
        assertEquals("my-util", has.getStopByRule());
    }

    @Test
    public void stopByRuleReferencingUnknownUtilRejected() {
        // roadmap item 24: the stopBy=rule horizon resolves through the same
        // utils registry as 'matches' — an unknown reference is a parse-time
        // rejection, never a runtime surprise
        DynamicObject model = ruleModel("demo/stop-by-rule-unknown", b -> b.addProp("has",
                relational("foo()", "rule", "no-such-util", null)));

        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(model));
        assertTrue(ex.getMessage().contains("demo/stop-by-rule-unknown"));
        assertTrue(ex.getMessage().contains("no-such-util"),
                "message must name the unknown util: " + ex.getMessage());
    }

    @Test
    public void fieldOnSiblingOperatorRejected() {
        DynamicObject model = ruleModel("demo/field-on-follows", b -> b.addProp("follows",
                relational("foo()", null, null, "body")));

        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(model));
        assertTrue(ex.getMessage().contains("demo/field-on-follows"));
        assertTrue(ex.getMessage().contains("inside/has"), "message must name the legal ops: "
                + ex.getMessage());
    }

    @Test
    public void contextualRelationalRoundTrips() {
        DynamicObject has = new DynamicObject("has");
        has.addProp("context", "use(Errors.$FIELD)");
        has.addProp("selector", "field_access");
        has.addProp("stopBy", "end");
        has.addProp("field", "body");
        RuleDslModel parsed = parser.parseRuleModel(ruleModel("demo/rel-contextual", b -> b.addProp("has", has)));

        RuleDslModel.Relational relational = parsed.getMatcher().getHas();
        assertNotNull(relational, "has matcher must be kept");
        assertNull(relational.getPattern(), "the contextual form carries no plain pattern");
        assertEquals("use(Errors.$FIELD)", relational.getContext());
        assertEquals("field_access", relational.getSelector());
        assertEquals("end", relational.getStopBy());
        assertEquals("body", relational.getField());
    }

    @Test
    public void contextualAndPlainPatternFormsAreExclusive() {
        DynamicObject has = new DynamicObject("has");
        has.addProp("pattern", "Errors.$FIELD");
        has.addProp("context", "use(Errors.$FIELD)");
        has.addProp("selector", "field_access");
        DynamicObject model = ruleModel("demo/rel-both-forms", b -> b.addProp("has", has));

        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(model));
        assertTrue(ex.getMessage().contains("demo/rel-both-forms"));
        assertTrue(ex.getMessage().contains("exclusive"), "message must state the exclusivity: "
                + ex.getMessage());
    }

    @Test
    public void selectorWithoutContextRejected() {
        DynamicObject has = new DynamicObject("has");
        has.addProp("selector", "field_access");
        DynamicObject model = ruleModel("demo/rel-selector-only", b -> b.addProp("has", has));

        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(model));
        assertTrue(ex.getMessage().contains("demo/rel-selector-only"));
        assertTrue(ex.getMessage().contains("context"), "message must name the missing context: "
                + ex.getMessage());
    }

    @Test
    public void contextWithoutSelectorRejected() {
        DynamicObject has = new DynamicObject("has");
        has.addProp("context", "use(Errors.$FIELD)");
        DynamicObject model = ruleModel("demo/rel-context-only", b -> b.addProp("has", has));

        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(model));
        assertTrue(ex.getMessage().contains("demo/rel-context-only"));
        assertTrue(ex.getMessage().contains("selector"), "message must name the missing selector: "
                + ex.getMessage());
    }

    @Test
    public void twoRelationalMatchersRejectedByTheExtendedXor() {
        DynamicObject model = ruleModel("demo/two-relational", b -> {
            b.addProp("inside", relational("foo()", null, null, null));
            b.addProp("has", relational("bar()", null, null, null));
        });

        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(model));
        assertTrue(ex.getMessage().contains("demo/two-relational"));
        assertTrue(ex.getMessage().contains("inside") && ex.getMessage().contains("has"),
                "message must name the conflicting fields: " + ex.getMessage());
    }

    @Test
    public void patternAndCompositeAreMutuallyExclusive() {
        DynamicObject model = ruleModel("demo/pattern-and-not", b -> {
            b.addProp("pattern", "foo()");
            b.addProp("not", notInner(relational("bar()", null, null, null)));
        });

        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(model));
        assertTrue(ex.getMessage().contains("demo/pattern-and-not"));
        assertTrue(ex.getMessage().contains("pattern") && ex.getMessage().contains("not"),
                "message must name the conflicting fields: " + ex.getMessage());
    }

    @Test
    public void allWithKindAndNotHasRoundTrips() {
        DynamicObject has = relational("throw $$$", "end", null, null);
        DynamicObject not = new DynamicObject("not");
        not.addProp("has", has);
        DynamicObject notElement = new DynamicObject("matcher");
        notElement.addProp("not", not);
        DynamicObject kindElement = new DynamicObject("matcher");
        kindElement.addProp("kind", "catch_clause");
        DynamicObject model = ruleModel("demo/all-roundtrip", b ->
                b.addProp("all", List.of(kindElement, notElement)));

        RuleDslModel parsed = parser.parseRuleModel(model);
        List<RuleDslModel.Matcher> all = parsed.getMatcher().getAll();
        assertNotNull(all);
        assertEquals(2, all.size());
        assertEquals("catch_clause", all.get(0).getKind());
        RuleDslModel.Matcher notMatcher = all.get(1).getNot();
        assertNotNull(notMatcher, "the all element's not inner must be kept");
        assertNotNull(notMatcher.getHas());
        assertEquals("throw $$$", notMatcher.getHas().getPattern());
        assertEquals("end", notMatcher.getHas().getStopBy());
    }

    @Test
    public void emptyAllRejected() {
        DynamicObject model = ruleModel("demo/all-empty", b -> b.addProp("all", List.of()));

        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(model));
        assertTrue(ex.getMessage().contains("demo/all-empty"));
        assertTrue(ex.getMessage().contains("without elements"));
    }

    @Test
    public void allElementWithTwoMatchersRejected() {
        DynamicObject element = new DynamicObject("matcher");
        element.addProp("kind", "catch_clause");
        element.addProp("regex", ".*");
        DynamicObject model = ruleModel("demo/all-two", b -> b.addProp("all", List.of(element)));

        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(model));
        assertTrue(ex.getMessage().contains("demo/all-two"));
        assertTrue(ex.getMessage().contains("'all' element #1"));
    }

    @Test
    public void anyInsideAllParsesAsNestedBranches() {
        // roadmap item 24 lifted the bounded-nesting surface: 'any' inside an
        // all element is now the legal nested-object branch form
        DynamicObject branch = new DynamicObject("matcher");
        branch.addProp("pattern", "foo()");
        DynamicObject element = new DynamicObject("matcher");
        element.addProp("any", List.of(branch));
        DynamicObject model = ruleModel("demo/any-in-all", b -> b.addProp("all", List.of(element)));

        RuleDslModel parsed = parser.parseRuleModel(model);
        List<RuleDslModel.Matcher> all = parsed.getMatcher().getAll();
        assertNotNull(all);
        assertNotNull(all.get(0).getAny(), "the nested any survives into the model");
        assertEquals(1, all.get(0).getAny().size());
        assertEquals("foo()", all.get(0).getAny().get(0).getPattern());
    }

    @Test
    public void emptyNestedAnyBranchStillRejected() {
        DynamicObject element = new DynamicObject("matcher");
        element.addProp("any", List.of(new DynamicObject("matcher")));
        DynamicObject model = ruleModel("demo/any-in-all-empty", b -> b.addProp("all", List.of(element)));

        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(model));
        assertTrue(ex.getMessage().contains("demo/any-in-all-empty"));
        assertTrue(ex.getMessage().contains("any' branch #1"),
                "message must name the empty branch: " + ex.getMessage());
    }

    @Test
    public void notWithoutInnerMatcherRejected() {
        DynamicObject model = ruleModel("demo/not-empty", b -> b.addProp("not",
                new DynamicObject("not")));

        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(model));
        assertTrue(ex.getMessage().contains("demo/not-empty"));
        assertTrue(ex.getMessage().contains("no matcher"), "message must state the missing matcher: "
                + ex.getMessage());
    }

    @Test
    public void notBelowAllElementNestsRecursively() {
        // roadmap item 24: the composite surface nests recursively — a not
        // inside an all element's not is legal and structurally finite
        DynamicObject innerNot = new DynamicObject("not");
        innerNot.addProp("pattern", "foo()");
        DynamicObject notNot = new DynamicObject("not");
        notNot.addProp("not", innerNot);
        DynamicObject element = new DynamicObject("matcher");
        element.addProp("not", notNot);
        DynamicObject model = ruleModel("demo/not-in-not", b -> b.addProp("all", List.of(element)));

        RuleDslModel parsed = parser.parseRuleModel(model);
        RuleDslModel.Matcher inner = parsed.getMatcher().getAll().get(0).getNot();
        assertNotNull(inner);
        assertNotNull(inner.getNot(), "the doubly nested not survives into the model");
        assertEquals("foo()", inner.getNot().getPattern());
    }

    @Test
    public void nestedNotWithoutInnerMatcherStillRejected() {
        DynamicObject notNot = new DynamicObject("not");
        notNot.addProp("not", new DynamicObject("not"));
        DynamicObject element = new DynamicObject("matcher");
        element.addProp("not", notNot);
        DynamicObject model = ruleModel("demo/not-in-not-empty", b -> b.addProp("all", List.of(element)));

        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(model));
        assertTrue(ex.getMessage().contains("demo/not-in-not-empty"));
        assertTrue(ex.getMessage().contains("no matcher"),
                "message must state the missing matcher: " + ex.getMessage());
    }

    @Test
    public void endToEndRelationalFixtureLoadsThroughXdef() {
        RuleDslModel model = parser.loadRuleModel(DIR + "valid-relational.rule.yml");

        assertEquals("demo/relational-roundtrip", model.getId());
        List<RuleDslModel.Matcher> all = model.getMatcher().getAll();
        assertNotNull(all, "the all matcher must survive the xdef pipeline");
        assertEquals(3, all.size());
        assertEquals("catch_clause", all.get(0).getKind());
        assertEquals("$E.getMessage()", all.get(1).getHas().getPattern());
        assertEquals("end", all.get(1).getHas().getStopBy());
        assertNotNull(all.get(2).getNot());
        assertEquals("throw $$$", all.get(2).getNot().getHas().getPattern());
    }

    @Test
    public void endToEndStopByRuleFixtureFailsClosed() {
        NopLintException ex = assertThrows(NopLintException.class,
                () -> parser.loadRuleModel(DIR + "invalid-stop-by-rule.rule.yml"),
                "stopBy=rule without stopByRule must fail closed through the full pipeline");
        assertTrue(ex.getMessage().contains("demo/stop-by-rule-missing"),
                "message must contain the rule id: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("stopByRule"));
    }

    // ==================== helpers (item 23) ====================

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

    private DynamicObject notInner(DynamicObject inner) {
        DynamicObject not = new DynamicObject("not");
        not.addProp("has", inner);
        return not;
    }

    // ==================== helpers ====================

    private DynamicObject utilsWithPattern(String utilId, String pattern) {
        DynamicObject util = new DynamicObject("util");
        util.addProp("pattern", pattern);
        DynamicObject utils = new DynamicObject("utils");
        utils.addProp(utilId, util);
        return utils;
    }

    private DynamicObject ruleModel(String id, java.util.function.Consumer<DynamicObject> ruleConfigurer) {
        DynamicObject model = new DynamicObject("lint-rule");
        model.addProp("id", id);
        DynamicObject rule = new DynamicObject("rule");
        ruleConfigurer.accept(rule);
        model.addProp("rule", rule);
        return model;
    }

    private DynamicObject patternModel(String id) {
        return patternModel(id, null);
    }

    private DynamicObject patternModel(String id, Integer timeoutMs) {
        DynamicObject model = new DynamicObject("lint-rule");
        model.addProp("id", id);
        DynamicObject rule = new DynamicObject("rule");
        rule.addProp("pattern", "foo($x)");
        model.addProp("rule", rule);
        if (timeoutMs != null)
            model.addProp("xscriptTimeoutMs", timeoutMs);
        return model;
    }
}
