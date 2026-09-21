package io.nop.lint.core.engine;

import io.nop.lint.core.NopLintException;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.model.object.DynamicObject;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.lang.TreeSitterLanguageAdapter;
import io.nop.lint.core.node.LintNode;
import io.nop.lint.core.node.LintTree;
import io.nop.lint.core.pattern.Match;
import io.nop.lint.core.rule.RuleDslModel;
import io.nop.lint.core.rule.RuleDslParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Form execution matrix proofs for {@link CompiledRule}: every matrix cell
 * has an assertion — pattern/kind/any execute with their declared semantics,
 * xscript rules compile into an executable engine and regex forms fail
 * closed at compile time (messages carry the rule id), target kind unions
 * come out of the fixtures, and the kind filter decision is observable via
 * {@code canMatchKinds}.
 */
public class TestCompiledRule {

    private static final String DIR = "/test/lint/rules/";

    private static final LintLanguage JAVA = new TreeSitterLanguageAdapter("java",
            io.nop.treesitter.language.Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin"), null);

    private static final String PRINT_SRC = "class Demo { void m() { System.out.println(\"x\"); } }";

    private static final String MIXED_SRC = """
            class Demo {
                void m(int x) {
                    System.out.println("x");
                    if (x > 0) {
                        throw new IllegalStateException("boom");
                    }
                }
            }
            """;

    private final RuleDslParser parser = new RuleDslParser();

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    // ==================== execute cells: pattern / kind / any ====================

    @Test
    public void patternMatcherExecutesAndReportsTargetKind() {
        CompiledRule rule = CompiledRule.compile(patternRule("demo/p", "System.out.println($$$ARGS)"), JAVA);
        List<LintNode> matches = rule.match(JAVA.parse(PRINT_SRC));

        assertEquals(1, matches.size());
        assertEquals("System.out.println(\"x\")", matches.get(0).text());
        assertArrayEquals(new int[]{JAVA.kindId("method_invocation")}, rule.targetKindIds(),
                "the pattern root's kind is the rule's target kind");
        assertEquals("warning", rule.severity());
        assertEquals("msg demo/p", rule.message());
    }

    @Test
    public void kindMatcherExecutesWholeTreeTraversal() {
        CompiledRule rule = CompiledRule.compile(kindRule("demo/k", "throw_statement"), JAVA);
        List<LintNode> matches = rule.match(JAVA.parse(MIXED_SRC));

        assertEquals(1, matches.size());
        assertEquals("throw_statement", matches.get(0).kind());
        assertArrayEquals(new int[]{JAVA.kindId("throw_statement")}, rule.targetKindIds());
    }

    @Test
    public void anyBranchesExecuteInBranchOrder() {
        CompiledRule rule = CompiledRule.compile(anyRule("demo/any", List.of(
                branch(b -> b.addProp("pattern", "System.out.println($$$ARGS)")),
                branch(b -> b.addProp("kind", "throw_statement")))), JAVA);

        List<LintNode> matches = rule.match(JAVA.parse(MIXED_SRC));

        assertEquals(2, matches.size(), "pattern branch and kind branch each report one node");
        assertEquals("System.out.println(\"x\")", matches.get(0).text(), "branch order is preserved");
        assertEquals("throw_statement", matches.get(1).kind());
        assertEquals(Set.of(JAVA.kindId("method_invocation"), JAVA.kindId("throw_statement")),
                toSet(rule.targetKindIds()), "any branches contribute their union");
    }

    @Test
    public void anyBranchPatternKindConjunctionRespected() {
        CompiledRule live = CompiledRule.compile(anyRule("demo/conj-live", List.of(
                branch(b -> {
                    b.addProp("pattern", "new IllegalStateException($A)");
                    b.addProp("kind", "object_creation_expression");
                }))), JAVA);
        CompiledRule dead = CompiledRule.compile(anyRule("demo/conj-dead", List.of(
                branch(b -> {
                    b.addProp("pattern", "new IllegalStateException($A)");
                    b.addProp("kind", "throw_statement");
                }))), JAVA);

        assertEquals(1, live.match(JAVA.parse(MIXED_SRC)).size(),
                "pattern + matching kind conjunct must report the node");
        assertEquals(0, dead.match(JAVA.parse(MIXED_SRC)).size(),
                "kind conjunct disjoint from the pattern root kind must yield nothing");
        assertArrayEquals(new int[]{JAVA.kindId("object_creation_expression")}, live.targetKindIds());
        assertEquals(0, dead.targetKindIds().length,
                "an unsatisfiable conjunct must not contribute target kinds (no-opinion union stays honest)");
    }

    @Test
    public void fixtureAnyTargetKindUnion() {
        RuleDslModel model = parser.loadRuleModel(DIR + "valid-any.rule.yml");
        CompiledRule rule = CompiledRule.compile(model, JAVA);

        assertEquals("demo/no-dynamic-dispatch", rule.ruleId());
        assertArrayEquals(new int[]{JAVA.kindId("method_invocation")}, rule.targetKindIds(),
                "valid-any branches all root on method_invocation: the union is that single kind");
        List<LintNode> matches = rule.match(JAVA.parse(
                "class Demo { void m() { foo.invoke(\"x\"); bar.getClass().getMethod(\"y\"); } }"));
        assertEquals(2, matches.size(), "both branches must fire on their shapes");
    }

    @Test
    public void metaVarRootPatternHasNoKindOpinion() {
        CompiledRule rule = CompiledRule.compile(patternRule("demo/meta", "$VAR"), JAVA);

        assertEquals(0, rule.targetKindIds().length, "a meta-var root matches any named kind");
        assertTrue(rule.canMatchKinds(Set.of()), "no kind opinion means the filter never excludes it");
        assertTrue(rule.match(JAVA.parse("class A { int x = 1; }")).size() > 0);
    }

    // ==================== reject cells: regex / xscript / unknown kind ====================

    @Test
    public void regexMatcherRejectedAtCompileTime() {
        NopLintException ex = assertThrows(NopLintException.class,
                () -> CompiledRule.compile(regexRule("demo/re"), JAVA));
        assertTrue(ex.getMessage().contains("demo/re"), "message must carry the rule id: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("regex"), "message must name the rejected form: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("22"), "message must point at the successor item: " + ex.getMessage());
    }

    @Test
    public void anyBranchRegexRejectedAtCompileTime() {
        NopLintException ex = assertThrows(NopLintException.class,
                () -> CompiledRule.compile(anyRule("demo/any-re", List.of(
                        branch(b -> b.addProp("regex", "foo\\(.*\\)")))), JAVA));
        assertTrue(ex.getMessage().contains("demo/any-re"), "message must carry the rule id: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("regex") && ex.getMessage().contains("branch"),
                "message must name the branch form: " + ex.getMessage());
    }

    @Test
    public void xscriptRuleCompilesIntoExecutableEngine() {
        RuleDslModel model = parser.loadRuleModel(DIR + "valid-full.rule.yml");
        CompiledRule rule = CompiledRule.compile(model, JAVA);

        assertNotNull(rule.xscriptEngine(), "an xscript rule must carry an executable script engine");
        assertEquals("demo/no-file-stream", rule.xscriptEngine().ruleId(),
                "the engine carries the rule id for diagnostic attribution");

        LintTree tree = JAVA.parse(
                "class Demo { void m() throws Exception { new FileInputStream(\"a\"); } }");
        List<Match> matches = rule.matchWithCaptures(tree);
        assertEquals(1, matches.size());
        assertEquals("new FileInputStream(\"a\")", matches.get(0).node().text());
        assertNotNull(matches.get(0).env().getCapture("F"),
                "the pattern capture must reach the xscript binding layer");
    }

    @Test
    public void unknownKindRejectedAtCompileTime() {
        NopLintException ex = assertThrows(NopLintException.class,
                () -> CompiledRule.compile(kindRule("demo/bad-kind", "not_a_kind"), JAVA));
        assertTrue(ex.getMessage().contains("demo/bad-kind"), "message must carry the rule id: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("not_a_kind"), "message must carry the kind name: " + ex.getMessage());
    }

    @Test
    public void ruleWithoutMatcherRejectedAtCompileTime() {
        DynamicObject model = new DynamicObject("lint-rule");
        model.addProp("id", "demo/nomatch");
        model.addProp("severity", "warning");
        model.addProp("message", "m");

        NopLintException ex = assertThrows(NopLintException.class,
                () -> CompiledRule.compile(parser.parseRuleModel(model), JAVA));
        assertTrue(ex.getMessage().contains("demo/nomatch"));
    }

    // ==================== kind filter decision ====================

    @Test
    public void canMatchKindsIntersectsTargetKinds() {
        CompiledRule rule = CompiledRule.compile(patternRule("demo/p", "System.out.println($$$ARGS)"), JAVA);
        int methodInvocation = JAVA.kindId("method_invocation");

        assertTrue(rule.canMatchKinds(Set.of(methodInvocation)));
        assertFalse(rule.canMatchKinds(Set.of(JAVA.kindId("throw_statement"))),
                "a disjoint kind set must let the engine skip the matcher");
        assertFalse(rule.canMatchKinds(Set.of()), "an empty file kind set must skip the matcher");
    }

    @Test
    public void targetKindIdsDefensiveCopy() {
        CompiledRule rule = CompiledRule.compile(patternRule("demo/p", "System.out.println($$$ARGS)"), JAVA);
        int[] first = rule.targetKindIds();
        first[0] = -12345;
        assertNotEquals(first[0], rule.targetKindIds()[0], "exposed arrays must not alias internal state");
    }

    // ==================== helpers ====================

    private RuleDslModel patternRule(String id, String pattern) {
        return parser.parseRuleModel(ruleModel(id, b -> b.addProp("pattern", pattern)));
    }

    private RuleDslModel kindRule(String id, String kind) {
        return parser.parseRuleModel(ruleModel(id, b -> b.addProp("kind", kind)));
    }

    private RuleDslModel regexRule(String id) {
        return parser.parseRuleModel(ruleModel(id, b -> b.addProp("regex", "foo\\(.*\\)")));
    }

    private RuleDslModel anyRule(String id, List<DynamicObject> branches) {
        return parser.parseRuleModel(ruleModel(id, b -> b.addProp("any", branches)));
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

    private static DynamicObject branch(java.util.function.Consumer<DynamicObject> configurer) {
        DynamicObject branch = new DynamicObject("matcher");
        configurer.accept(branch);
        return branch;
    }

    private static Set<Integer> toSet(int[] values) {
        return java.util.Arrays.stream(values).boxed().collect(java.util.stream.Collectors.toSet());
    }
}
