package io.nop.lint.core.rule;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.model.object.DynamicObject;
import io.nop.lint.core.NopLintException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The constraints parsing matrix (roadmap item 22, design 01 §3.2/§3.3,
 * Minimum Rules #25): every legal constraint kind round-trips with its
 * normalized fields, and every illegal shape — unknown constraint name,
 * multiple constraint keys per element, missing required sub-field, empty or
 * undersized captures, blank values, non-integer/negative withinDepth max,
 * invalid capture reference shape, typeOf without its {@code requires: "L2"}
 * declaration — fails closed with a {@link NopLintException} naming the rule
 * id (Minimum Rules #24). End-to-end legs prove the registered
 * {@code rule.yml} pipeline carries the top-level constraints field through
 * xdef (constraints never enter the rule container).
 */
public class TestConstraintParsing {

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

    // ==================== legal forms: every kind round-trips ====================

    @Test
    public void sameTextRoundTripsWithNormalizedCaptures() {
        RuleDslModel.Constraint constraint = singleConstraint(constraintElement("sameText", e ->
                e.addProp("captures", List.of("$A", "$B"))));

        assertEquals("sameText", constraint.getKind());
        assertEquals(List.of("A", "B"), constraint.getCaptures(), "the $ prefix must normalize away");
        assertNull(constraint.getCapture());
        assertNull(constraint.getPattern());
        assertNull(constraint.getMax());
    }

    @Test
    public void differentTextRoundTrips() {
        RuleDslModel.Constraint constraint = singleConstraint(constraintElement("differentText", e ->
                e.addProp("captures", List.of("A", "B"))));
        assertEquals("differentText", constraint.getKind());
        assertEquals(List.of("A", "B"), constraint.getCaptures());
    }

    @Test
    public void regexRoundTripsWithCaptureAndPattern() {
        RuleDslModel.Constraint constraint = singleConstraint(constraintElement("regex", e -> {
            e.addProp("capture", "$NAME");
            e.addProp("pattern", "^[a-z]+$");
        }));
        assertEquals("regex", constraint.getKind());
        assertEquals("NAME", constraint.getCapture());
        assertEquals("^[a-z]+$", constraint.getPattern());
    }

    @Test
    public void inListRoundTripsWithValues() {
        RuleDslModel.Constraint constraint = singleConstraint(constraintElement("inList", e -> {
            e.addProp("capture", "$M");
            e.addProp("values", List.of("get", "post", "put", "delete"));
        }));
        assertEquals("inList", constraint.getKind());
        assertEquals("M", constraint.getCapture());
        assertEquals(List.of("get", "post", "put", "delete"), constraint.getValues());
    }

    @Test
    public void typeOfRoundTripsWithRequiredL2Declaration() {
        RuleDslModel parsed = parser.parseRuleModel(ruleModelWithPattern("demo/typeof-ok", "$X.foo()",
                List.of(constraintElement("typeOf", e -> {
                    e.addProp("capture", "$X");
                    e.addProp("is", "String");
                })), "L2"));

        RuleDslModel.Constraint constraint = parsed.getConstraints().get(0);
        assertEquals("typeOf", constraint.getKind());
        assertEquals("X", constraint.getCapture());
        assertEquals("String", constraint.getIs());
        assertEquals(Set.of("L2"), parsed.getRequires());
    }

    @Test
    public void notExistsRoundTripsWithReservedMessage() {
        RuleDslModel.Constraint constraint = singleConstraint(constraintElement("notExists", e -> {
            e.addProp("pattern", "return null;");
            e.addProp("message", "no null return");
        }));
        assertEquals("notExists", constraint.getKind());
        assertEquals("return null;", constraint.getPattern());
        assertEquals("no null return", constraint.getMessage(),
                "the reserved message field must survive round-trip (never consulted by the "
                        + "v1 filter evaluator, but observable on the model)");
    }

    @Test
    public void withinDepthRoundTrips() {
        RuleDslModel.Constraint constraint = singleConstraint(constraintElement("withinDepth", e ->
                e.addProp("max", 3)));
        assertEquals("withinDepth", constraint.getKind());
        assertEquals(3, constraint.getMax());
        assertNull(constraint.getMessage(), "withinDepth carries no message field (design 01 §3.3)");
    }

    @Test
    public void emptyConstraintsListIsLegal() {
        DynamicObject model = new DynamicObject("lint-rule");
        model.addProp("id", "demo/constraints-empty");
        DynamicObject rule = new DynamicObject("rule");
        rule.addProp("pattern", "foo($x)");
        model.addProp("rule", rule);
        model.addProp("constraints", List.of());

        RuleDslModel parsed = parser.parseRuleModel(model);
        assertTrue(parsed.getConstraints().isEmpty(),
                "constraints: [] is the design 01 §2 schema placeholder and must stay empty");
    }

    @Test
    public void allSevenKindsCoexistInOneConstraintsList() {
        DynamicObject model = ruleModelWithPattern("demo/constraints-all", "$A == $B",
                List.of(
                        constraintElement("sameText", e -> e.addProp("captures", List.of("$A", "$B"))),
                        constraintElement("differentText", e -> e.addProp("captures", List.of("$A", "$C"))),
                        constraintElement("regex", e -> {
                            e.addProp("capture", "$A");
                            e.addProp("pattern", ".*");
                        }),
                        constraintElement("inList", e -> {
                            e.addProp("capture", "$B");
                            e.addProp("values", List.of("x", "y"));
                        }),
                        constraintElement("typeOf", e -> {
                            e.addProp("capture", "$A");
                            e.addProp("is", "String");
                        }),
                        constraintElement("notExists", e -> e.addProp("pattern", "return null;")),
                        constraintElement("withinDepth", e -> e.addProp("max", 2))),
                "L2");

        RuleDslModel parsed = parser.parseRuleModel(model);
        assertEquals(7, parsed.getConstraints().size());
        for (int i = 0; i < 7; i++) {
            assertEquals(KINDS[i], parsed.getConstraints().get(i).getKind(),
                    "element order must be preserved");
        }
    }

    // ==================== illegal forms: fail-closed matrix ====================

    @Test
    public void unknownConstraintKindRejected() {
        DynamicObject element = new DynamicObject("constraint");
        DynamicObject body = new DynamicObject("sameTextx");
        body.addProp("captures", List.of("$A", "$B"));
        element.addProp("sameTextx", body);
        DynamicObject model = ruleModel("demo/constraint-unknown", List.of(element));

        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(model));
        assertTrue(ex.getMessage().contains("demo/constraint-unknown"),
                "message must contain the rule id: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("constraint"),
                "message must name the constraint problem: " + ex.getMessage());
    }

    @Test
    public void twoConstraintKindsInOneElementRejected() {
        DynamicObject element = constraintElement("sameText", e -> e.addProp("captures", List.of("$A", "$B")));
        DynamicObject second = new DynamicObject("withinDepth");
        second.addProp("max", 2);
        element.addProp("withinDepth", second);
        DynamicObject model = ruleModel("demo/constraint-two-kinds", List.of(element));

        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(model));
        assertTrue(ex.getMessage().contains("demo/constraint-two-kinds"));
        assertTrue(ex.getMessage().contains("multiple constraints"),
                "message must state the XOR violation: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("sameText") && ex.getMessage().contains("withinDepth"),
                "message must name the conflicting kinds: " + ex.getMessage());
    }

    @Test
    public void emptyConstraintElementRejected() {
        DynamicObject model = ruleModel("demo/constraint-empty",
                List.of(new DynamicObject("constraint")));

        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(model));
        assertTrue(ex.getMessage().contains("demo/constraint-empty"));
        assertTrue(ex.getMessage().contains("no constraint"));
    }

    @Test
    public void nonObjectConstraintElementRejected() {
        DynamicObject model = ruleModel("demo/constraint-scalar",
                List.of("sameText"));

        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(model));
        assertTrue(ex.getMessage().contains("demo/constraint-scalar"));
        assertTrue(ex.getMessage().contains("non-object"));
    }

    @Test
    public void nonListConstraintsRejected() {
        DynamicObject model = new DynamicObject("lint-rule");
        model.addProp("id", "demo/constraints-scalar");
        DynamicObject rule = new DynamicObject("rule");
        rule.addProp("pattern", "foo($x)");
        model.addProp("rule", rule);
        model.addProp("constraints", "sameText");

        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(model));
        assertTrue(ex.getMessage().contains("demo/constraints-scalar"));
        assertTrue(ex.getMessage().contains("non-list"));
    }

    @Test
    public void sameTextWithEmptyCapturesRejected() {
        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(
                ruleModel("demo/same-text-empty", List.of(constraintElement("sameText", e ->
                        e.addProp("captures", List.of()))))));
        assertTrue(ex.getMessage().contains("demo/same-text-empty"));
        assertTrue(ex.getMessage().contains("'captures'"), "message must name the missing field: "
                + ex.getMessage());
    }

    @Test
    public void sameTextWithSingleCaptureRejected() {
        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(
                ruleModel("demo/same-text-one", List.of(constraintElement("sameText", e ->
                        e.addProp("captures", List.of("$A")))))));
        assertTrue(ex.getMessage().contains("demo/same-text-one"));
        assertTrue(ex.getMessage().contains("at least two captures"),
                "message must state the size rule: " + ex.getMessage());
    }

    @Test
    public void blankCaptureEntryRejected() {
        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(
                ruleModel("demo/same-text-blank", List.of(constraintElement("sameText", e ->
                        e.addProp("captures", List.of("$A", " ")))))));
        assertTrue(ex.getMessage().contains("demo/same-text-blank"));
        assertTrue(ex.getMessage().contains("blank"));
    }

    @Test
    public void invalidCaptureReferenceShapeRejected() {
        DynamicObject element = constraintElement("sameText", e ->
                e.addProp("captures", List.of("$A", "$b!")));
        DynamicObject model = ruleModel("demo/capture-shape", List.of(element));

        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(model));
        assertTrue(ex.getMessage().contains("demo/capture-shape"));
        assertTrue(ex.getMessage().contains("$b!"), "message must quote the offending raw text: "
                + ex.getMessage());
        assertTrue(ex.getMessage().contains("[A-Z_][A-Z_0-9]*"),
                "message must state the name grammar: " + ex.getMessage());
    }

    @Test
    public void regexWithoutPatternRejected() {
        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(
                ruleModel("demo/regex-no-pattern", List.of(constraintElement("regex", e ->
                        e.addProp("capture", "$A"))))));
        assertTrue(ex.getMessage().contains("demo/regex-no-pattern"));
        assertTrue(ex.getMessage().contains("'pattern'"));
    }

    @Test
    public void regexWithSyntacticallyInvalidPatternRejected() {
        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(
                ruleModel("demo/regex-bad", List.of(constraintElement("regex", e -> {
                    e.addProp("capture", "$A");
                    e.addProp("pattern", "[unclosed");
                })))));
        assertTrue(ex.getMessage().contains("demo/regex-bad"));
        assertTrue(ex.getMessage().contains("invalid regex"), "message must state the regex problem: "
                + ex.getMessage());
    }

    @Test
    public void regexWithoutCaptureRejected() {
        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(
                ruleModel("demo/regex-no-capture", List.of(constraintElement("regex", e ->
                        e.addProp("pattern", ".*"))))));
        assertTrue(ex.getMessage().contains("demo/regex-no-capture"));
        assertTrue(ex.getMessage().contains("'capture'"));
    }

    @Test
    public void inListWithoutValuesRejected() {
        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(
                ruleModel("demo/in-list-no-values", List.of(constraintElement("inList", e ->
                        e.addProp("capture", "$M"))))));
        assertTrue(ex.getMessage().contains("demo/in-list-no-values"));
        assertTrue(ex.getMessage().contains("'values'"));
    }

    @Test
    public void inListWithEmptyValuesRejected() {
        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(
                ruleModel("demo/in-list-empty", List.of(constraintElement("inList", e -> {
                    e.addProp("capture", "$M");
                    e.addProp("values", List.of());
                })))));
        assertTrue(ex.getMessage().contains("demo/in-list-empty"));
        assertTrue(ex.getMessage().contains("'values'"));
    }

    @Test
    public void inListWithBlankValueEntryRejected() {
        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(
                ruleModel("demo/in-list-blank", List.of(constraintElement("inList", e -> {
                    e.addProp("capture", "$M");
                    e.addProp("values", List.of("get", ""));
                })))));
        assertTrue(ex.getMessage().contains("demo/in-list-blank"));
        assertTrue(ex.getMessage().contains("blank"));
    }

    @Test
    public void typeOfWithoutIsRejected() {
        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(
                ruleModelWithPattern("demo/typeof-no-is", "$X.foo()",
                        List.of(constraintElement("typeOf", e -> e.addProp("capture", "$X"))), "L2")));
        assertTrue(ex.getMessage().contains("demo/typeof-no-is"));
        assertTrue(ex.getMessage().contains("'is'"));
    }

    @Test
    public void typeOfWithoutL2RequiresRejectedFailClosed() {
        DynamicObject model = ruleModelWithPattern("demo/typeof-no-l2", "$X.foo()",
                List.of(constraintElement("typeOf", e -> {
                    e.addProp("capture", "$X");
                    e.addProp("is", "String");
                })), null);

        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(model));
        assertTrue(ex.getMessage().contains("demo/typeof-no-l2"),
                "message must contain the rule id: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("typeOf") && ex.getMessage().contains("L2"),
                "message must name the constraint and the gate: " + ex.getMessage());
    }

    @Test
    public void typeOfWithL1OnlyRequiresRejected() {
        DynamicObject model = ruleModelWithPattern("demo/typeof-l1-only", "$X.foo()",
                List.of(constraintElement("typeOf", e -> {
                    e.addProp("capture", "$X");
                    e.addProp("is", "String");
                })), "L1");

        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(model));
        assertTrue(ex.getMessage().contains("demo/typeof-l1-only"));
        assertTrue(ex.getMessage().contains("L2"),
                "the L1-only declaration must not satisfy the typeOf gate: " + ex.getMessage());
    }

    @Test
    public void notExistsWithoutPatternRejected() {
        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(
                ruleModel("demo/not-exists-no-pattern", List.of(constraintElement("notExists", e ->
                        e.addProp("message", "unused"))))));
        assertTrue(ex.getMessage().contains("demo/not-exists-no-pattern"));
        assertTrue(ex.getMessage().contains("'pattern'"));
    }

    @Test
    public void withinDepthWithoutMaxRejected() {
        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(
                ruleModel("demo/depth-no-max", List.of(constraintElement("withinDepth", e -> {
                })))));
        assertTrue(ex.getMessage().contains("demo/depth-no-max"));
        assertTrue(ex.getMessage().contains("'max'"));
    }

    @Test
    public void withinDepthWithNonIntegerMaxRejected() {
        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(
                ruleModel("demo/depth-non-int", List.of(constraintElement("withinDepth", e ->
                        e.addProp("max", "deep"))))));
        assertTrue(ex.getMessage().contains("demo/depth-non-int"));
        assertTrue(ex.getMessage().contains("non-integer"));
    }

    @Test
    public void withinDepthWithFractionalMaxRejected() {
        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(
                ruleModel("demo/depth-fraction", List.of(constraintElement("withinDepth", e ->
                        e.addProp("max", 2.5))))));
        assertTrue(ex.getMessage().contains("demo/depth-fraction"));
        assertTrue(ex.getMessage().contains("non-integer"));
    }

    @Test
    public void withinDepthWithNegativeMaxRejected() {
        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(
                ruleModel("demo/depth-negative", List.of(constraintElement("withinDepth", e ->
                        e.addProp("max", -1))))));
        assertTrue(ex.getMessage().contains("demo/depth-negative"));
        assertTrue(ex.getMessage().contains("negative"));
    }

    @Test
    public void withinDepthWithZeroMaxIsLegal() {
        RuleDslModel.Constraint constraint = singleConstraint(constraintElement("withinDepth", e ->
                e.addProp("max", 0)));
        assertEquals(0, constraint.getMax(), "max 0 means the match node itself must be a leaf");
    }

    // ==================== end to end: fixture path -> typed model ====================

    @Test
    public void endToEndConstraintsFixtureLoadsThroughXdef() {
        RuleDslModel model = parser.loadRuleModel(DIR + "valid-constraints.rule.yml");

        assertEquals("demo/constraints-roundtrip", model.getId());
        assertEquals(Set.of("L2"), model.getRequires());
        List<RuleDslModel.Constraint> constraints = model.getConstraints();
        assertEquals(7, constraints.size(), "the fixture declares one element per constraint kind");

        RuleDslModel.Constraint sameText = constraints.get(0);
        assertEquals("sameText", sameText.getKind());
        assertEquals(List.of("A", "B"), sameText.getCaptures(), "YAML list captures must normalize");

        RuleDslModel.Constraint differentText = constraints.get(1);
        assertEquals(List.of("A", "C"), differentText.getCaptures(),
                "CSV string captures must split through the csv-set pipeline");

        RuleDslModel.Constraint regex = constraints.get(2);
        assertEquals("^[a-z]+$", regex.getPattern());
        assertEquals("NAME", regex.getCapture());

        RuleDslModel.Constraint inList = constraints.get(3);
        assertEquals(List.of("get", "post", "put", "delete"), inList.getValues());

        RuleDslModel.Constraint typeOf = constraints.get(4);
        assertEquals("String", typeOf.getIs());

        RuleDslModel.Constraint notExists = constraints.get(5);
        assertEquals("return null;", notExists.getPattern());
        assertEquals("no null returns below the match", notExists.getMessage());

        RuleDslModel.Constraint withinDepth = constraints.get(6);
        assertEquals(3, withinDepth.getMax());
    }

    @Test
    public void endToEndUnknownConstraintFixtureFailsClosed() {
        NopLintException ex = assertThrows(NopLintException.class,
                () -> parser.loadRuleModel(DIR + "invalid-constraints-unknown.rule.yml"),
                "an unknown constraint kind must fail closed through the full pipeline");
        // Two fail-closed layers reject this shape: the xdef structural
        // validation (unknown-prop, naming the offending key) or, for shapes
        // it cannot see, the RuleDslParser semantic validation (naming the
        // rule id). Either rejection satisfies the no-silent-skip contract.
        assertTrue(ex.getMessage().contains("sameTextx")
                        || ex.getMessage().contains("demo/constraints-unknown"),
                "message must name the offending constraint or the rule id: " + ex.getMessage());
    }

    @Test
    public void endToEndTypeOfWithoutL2FixtureFailsClosed() {
        NopLintException ex = assertThrows(NopLintException.class,
                () -> parser.loadRuleModel(DIR + "invalid-constraints-typeof-no-l2.rule.yml"),
                "typeOf without requires:L2 must fail closed through the full pipeline");
        assertTrue(ex.getMessage().contains("demo/constraints-typeof-no-l2"));
        assertTrue(ex.getMessage().contains("L2"));
    }

    // ==================== helpers ====================

    static final String[] KINDS = {"sameText", "differentText", "regex", "inList", "typeOf",
            "notExists", "withinDepth"};

    private RuleDslModel.Constraint singleConstraint(DynamicObject element) {
        RuleDslModel parsed = parser.parseRuleModel(ruleModel("demo/constraint-single", List.of(element)));
        assertEquals(1, parsed.getConstraints().size());
        return parsed.getConstraints().get(0);
    }

    private DynamicObject constraintElement(String kind, java.util.function.Consumer<DynamicObject> bodyConfigurer) {
        DynamicObject element = new DynamicObject("constraint");
        DynamicObject body = new DynamicObject(kind);
        bodyConfigurer.accept(body);
        element.addProp(kind, body);
        return element;
    }

    /**
     * A rule model with the default pattern matcher and the given
     * top-level {@code constraints} list (constraints never enter the rule
     * container — design 01 §2 schema convention).
     */
    private DynamicObject ruleModel(String id, List<?> constraints) {
        DynamicObject model = new DynamicObject("lint-rule");
        model.addProp("id", id);
        DynamicObject rule = new DynamicObject("rule");
        rule.addProp("pattern", "foo($x)");
        model.addProp("rule", rule);
        if (constraints != null && !constraints.isEmpty())
            model.addProp("constraints", constraints);
        return model;
    }

    /**
     * A rule model with an explicit container pattern, top-level constraints,
     * and a CSV {@code requires} declaration (null = absent).
     */
    private DynamicObject ruleModelWithPattern(String id, String pattern,
                                               List<?> constraints, String requiresCsv) {
        DynamicObject model = ruleModel(id, constraints);
        DynamicObject rule = (DynamicObject) model.prop_get("rule");
        rule.prop_set("pattern", pattern);
        if (requiresCsv != null)
            model.addProp("requires", requiresCsv);
        return model;
    }
}
