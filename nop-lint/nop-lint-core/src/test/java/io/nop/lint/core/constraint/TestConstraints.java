package io.nop.lint.core.constraint;

import io.nop.core.model.object.DynamicObject;
import io.nop.lint.core.NopLintException;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.node.LintNode;
import io.nop.lint.core.node.LintTree;
import io.nop.lint.core.pattern.MetaVarEnv;
import io.nop.lint.core.pattern.SourcePattern;
import io.nop.lint.core.pattern.SourcePatternCompiler;
import io.nop.lint.core.rule.RuleDslModel;
import io.nop.lint.core.rule.RuleDslParser;
import io.nop.lint.core.testing.JavaBindingTestSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compile-time contract of the constraint compiler (roadmap item 22):
 * capture references must be declared by the rule's matcher as single-node
 * captures — undeclared names and sequence captures are rejected with the
 * rule id, the constraint kind, and the capture name; the notExists inner
 * pattern compiles fail-closed and evaluates in per-match subtree scope; the
 * typeOf evaluation branch is a guarded fail, never a silent guess. The
 * declared-capture extraction ({@code SourcePattern.captureNames}) is pinned
 * here too. Constraint models are built through the real parser path — the
 * package-private constructor keeps parser-only construction.
 */
public class TestConstraints {

    private static final LintLanguage JAVA = JavaBindingTestSupport.javaBinding();

    @Test
    public void patternExposesDeclaredCaptures() {
        SourcePattern pattern = SourcePatternCompiler.compile("$A == $B", JAVA);
        assertEquals(Set.of("A", "B"), pattern.captureNames());
        assertTrue(pattern.multiCaptureNames().isEmpty());

        SourcePattern withSeq = SourcePatternCompiler.compile("foo($$$ARGS, $X)", JAVA);
        assertEquals(Set.of("X"), withSeq.captureNames());
        assertEquals(Set.of("ARGS"), withSeq.multiCaptureNames());

        SourcePattern drops = SourcePatternCompiler.compile("$_IGNORED == $A", JAVA);
        assertEquals(Set.of("A"), drops.captureNames(),
                "drop names starting with _ never capture and must not enter the reference set");
    }

    @Test
    public void undeclaredCaptureRejectedWithRuleIdAndConstraintKind() {
        RuleDslModel.Constraint model = parseConstraint("demo/capture-check", element ->
                element.addProp("sameText", props("sameText", e ->
                        e.addProp("captures", List.of("$A", "$NOPE")))));

        NopLintException ex = assertThrows(NopLintException.class,
                () -> Constraints.compile(model, "demo/capture-check", JAVA,
                        Set.of("A", "B"), Set.of()));
        assertTrue(ex.getMessage().contains("demo/capture-check"),
                "message must contain the rule id: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("sameText"),
                "message must name the constraint kind: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("NOPE"),
                "message must name the offending capture: " + ex.getMessage());
    }

    @Test
    public void sequenceCaptureReferenceRejectedWithPreciseReason() {
        RuleDslModel.Constraint model = parseConstraint("demo/seq-capture", element ->
                element.addProp("inList", props("inList", e -> {
                    e.addProp("capture", "$ARGS");
                    e.addProp("values", List.of("x"));
                })));

        NopLintException ex = assertThrows(NopLintException.class,
                () -> Constraints.compile(model, "demo/seq-capture", JAVA,
                        Set.of("X"), Set.of("ARGS")));
        assertTrue(ex.getMessage().contains("demo/seq-capture"));
        assertTrue(ex.getMessage().contains("inList"));
        assertTrue(ex.getMessage().contains("sequence capture"),
                "message must distinguish sequence captures from undeclared names: "
                        + ex.getMessage());
    }

    @Test
    public void notExistsInnerPatternCompilesFailClosed() {
        RuleDslModel.Constraint model = parseConstraint("demo/bad-inner", element ->
                element.addProp("notExists", props("notExists", e ->
                        e.addProp("pattern", "return ]]"))));

        NopLintException ex = assertThrows(NopLintException.class,
                () -> Constraints.compile(model, "demo/bad-inner", JAVA, Set.of(), Set.of()));
        assertTrue(ex.getMessage().contains("demo/bad-inner"));
        assertTrue(ex.getMessage().contains("notExists"), "message must name the constraint: "
                + ex.getMessage());
        assertTrue(ex.getMessage().contains("inner pattern"), "message must state the failure site: "
                + ex.getMessage());
    }

    @Test
    public void notExistsCompilesAndEvaluatesSubtreeScope() {
        RuleDslModel.Constraint model = parseConstraint("demo/not-exists", element ->
                element.addProp("notExists", props("notExists", e ->
                        e.addProp("pattern", "return null;"))));
        Constraint compiled = Constraints.compile(model, "demo/not-exists", JAVA, Set.of(), Set.of());

        LintTree withNull =
                JAVA.parse("class T { Object m(boolean c) { if (c) return null; return null; } }");
        LintNode ifNode = findKind(withNull.root(), "if_statement");
        assertNotNull(ifNode, "the fixture source must contain an if_statement");
        MetaVarEnv emptyEnv = new MetaVarEnv();
        assertTrue(!compiled.holds(new ConstraintContext(ifNode, emptyEnv)),
                "the if subtree contains 'return null', so notExists must fail (match filtered)");

        LintTree withoutNull =
                JAVA.parse("class T { int m(boolean c) { if (c) return 1; return 0; } }");
        LintNode plainIf = findKind(withoutNull.root(), "if_statement");
        assertNotNull(plainIf);
        assertTrue(compiled.holds(new ConstraintContext(plainIf, emptyEnv)),
                "a subtree without 'return null' must satisfy notExists");
    }

    @Test
    public void typeOfEvaluationIsAGuardedFail() {
        // The rule declares requires: "L2", so the parser gate admits it —
        // evaluation is still unreachable because the engine's profile gate
        // skips the whole rule first; this test proves the evaluator itself
        // fails loudly if that second invariant is ever broken.
        RuleDslModel.Constraint model = parseConstraint("demo/typeof-guard", "L2", element ->
                element.addProp("typeOf", props("typeOf", e -> {
                    e.addProp("capture", "$X");
                    e.addProp("is", "String");
                })));
        Constraint compiled = Constraints.compile(model, "demo/typeof-guard", JAVA,
                Set.of("X"), Set.of());

        MetaVarEnv env = new MetaVarEnv();
        NopLintException ex = assertThrows(NopLintException.class,
                () -> compiled.holds(new ConstraintContext(JAVA.parse("class T {}").root(), env)));
        assertTrue(ex.getMessage().contains("L2"), "the guard must point at the L2 gate: "
                + ex.getMessage());
        assertTrue(ex.getMessage().contains("X") && ex.getMessage().contains("String"),
                "the guard must name the typeOf operands: " + ex.getMessage());
    }

    // ==================== helpers ====================

    /**
     * Builds one constraint model through the real parser path (constraints
     * are parser-constructed by design; the carrier constructor stays
     * package-private). No {@code requires} declaration.
     */
    private RuleDslModel.Constraint parseConstraint(String ruleId,
                                                    Consumer<DynamicObject> elementConfigurer) {
        return parseConstraint(ruleId, null, elementConfigurer);
    }

    /**
     * Same, with an explicit top-level {@code requires} CSV (null = absent) —
     * the typeOf gate needs {@code "L2"} for the model to parse at all.
     */
    private RuleDslModel.Constraint parseConstraint(String ruleId, String requiresCsv,
                                                    Consumer<DynamicObject> elementConfigurer) {
        DynamicObject element = new DynamicObject("constraint");
        elementConfigurer.accept(element);
        DynamicObject model = new DynamicObject("lint-rule");
        model.addProp("id", ruleId);
        DynamicObject rule = new DynamicObject("rule");
        rule.addProp("pattern", "foo($x)");
        model.addProp("rule", rule);
        model.addProp("constraints", List.of(element));
        if (requiresCsv != null)
            model.addProp("requires", requiresCsv);
        RuleDslModel parsed = new RuleDslParser().parseRuleModel(model);
        assertEquals(1, parsed.getConstraints().size());
        return parsed.getConstraints().get(0);
    }

    private DynamicObject props(String name, Consumer<DynamicObject> configurer) {
        DynamicObject body = new DynamicObject(name);
        configurer.accept(body);
        return body;
    }

    private LintNode findKind(LintNode root, String kind) {
        for (LintNode node : root) {
            if (kind.equals(node.kind()))
                return node;
        }
        return null;
    }
}
