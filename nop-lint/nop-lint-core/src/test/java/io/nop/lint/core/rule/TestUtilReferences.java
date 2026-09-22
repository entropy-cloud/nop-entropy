package io.nop.lint.core.rule;

import io.nop.core.model.object.DynamicObject;
import io.nop.lint.core.NopLintException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The parse-time fail-closed matrix of the utils/matches surface (roadmap
 * item 24 Phase 1): every {@code matches} reference and {@code stopBy=rule}
 * horizon must name a declared util, the reference graph must be cycle-free
 * (direct self-reference included — every cycle re-evaluates on the same
 * node and can never terminate), and a declared-but-empty utils container is
 * rejected. Models go through the real parser path.
 */
public class TestUtilReferences {

    private final RuleDslParser parser = new RuleDslParser();

    // ==================== matches references ====================

    @Test
    public void matchesReferenceToDeclaredUtilRoundTrips() {
        DynamicObject model = patternModelWithUtils("demo/matches-ok", "safe-close",
                utilsWithMatcher("safe-close", u -> u.addProp("pattern", "$X.close()")));

        RuleDslModel parsed = parser.parseRuleModel(model);
        assertEquals("safe-close", parsed.getMatcher().getMatches(),
                "the top-level matches reference survives into the model");
        assertNotNull(parsed.getUtils().get("safe-close"));
    }

    @Test
    public void matchesReferenceToUnknownUtilRejected() {
        DynamicObject model = patternModelWithUtils("demo/matches-unknown", "no-such-util",
                utilsWithMatcher("safe-close", u -> u.addProp("pattern", "$X.close()")));

        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(model));
        assertTrue(ex.getMessage().contains("demo/matches-unknown"));
        assertTrue(ex.getMessage().contains("no-such-util"),
                "message must name the unknown util: " + ex.getMessage());
    }

    @Test
    public void matchesReferenceWithoutAnyUtilsRejected() {
        DynamicObject model = patternModel("demo/matches-no-utils");
        ruleOf(model).obj_propValues().remove("pattern");
        ruleOf(model).prop_set("matches", "safe-close");

        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(model));
        assertTrue(ex.getMessage().contains("safe-close"), ex.getMessage());
    }

    @Test
    public void matchesInsideUtilResolvesAgainstSharedMap() {
        DynamicObject utils = new DynamicObject("utils");
        utils.addProp("safe-close", utilWith(u -> u.addProp("pattern", "$X.close()")));
        utils.addProp("indirect", utilWith(u -> u.addProp("matches", "safe-close")));
        DynamicObject model = patternModelWithUtils("demo/matches-chain", "indirect", utils);

        RuleDslModel parsed = parser.parseRuleModel(model);
        assertEquals("indirect", parsed.getMatcher().getMatches());
        assertEquals("safe-close", parsed.getUtils().get("indirect").getMatches(),
                "a util may reference another util (acyclic chain)");
    }

    // ==================== cycles ====================

    @Test
    public void selfReferencingUtilRejected() {
        DynamicObject utils = new DynamicObject("utils");
        utils.addProp("recursive", utilWith(u -> u.addProp("matches", "recursive")));
        DynamicObject model = patternModelWithUtils("demo/util-self-cycle", "recursive", utils);

        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(model));
        assertTrue(ex.getMessage().contains("demo/util-self-cycle"));
        assertTrue(ex.getMessage().contains("circular"), "message must name the cycle: "
                + ex.getMessage());
    }

    @Test
    public void indirectUtilCycleRejected() {
        DynamicObject utils = new DynamicObject("utils");
        utils.addProp("a", utilWith(u -> u.addProp("matches", "b")));
        utils.addProp("b", utilWith(u -> u.addProp("all", List.of(
                matcherWith(m -> m.addProp("pattern", "foo()")),
                matcherWith(m -> m.addProp("matches", "a"))))));
        DynamicObject model = patternModelWithUtils("demo/util-indirect-cycle", "a", utils);

        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(model));
        assertTrue(ex.getMessage().contains("circular"), "the indirect cycle must be named: "
                + ex.getMessage());
        assertTrue(ex.getMessage().contains("a -> b -> a") || ex.getMessage().contains("b -> a -> b"),
                "message should render the reference path: " + ex.getMessage());
    }

    @Test
    public void cycleThroughStopByRuleRejected() {
        DynamicObject utils = new DynamicObject("utils");
        utils.addProp("horizon", utilWith(u -> {
            DynamicObject inside = new DynamicObject("inside");
            inside.addProp("pattern", "foo()");
            inside.addProp("stopBy", "rule");
            inside.addProp("stopByRule", "horizon");
            u.addProp("inside", inside);
        }));
        DynamicObject model = patternModelWithUtils("demo/util-stopby-cycle", "horizon", utils);

        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(model));
        assertTrue(ex.getMessage().contains("circular"),
                "a stopBy=rule self-cycle is still a cycle: " + ex.getMessage());
    }

    // ==================== utils container ====================

    @Test
    public void emptyUtilsContainerRejected() {
        DynamicObject model = patternModel("demo/utils-empty");
        model.addProp("utils", new DynamicObject("utils"));

        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(model));
        assertTrue(ex.getMessage().contains("demo/utils-empty"));
        assertTrue(ex.getMessage().contains("empty 'utils'"), "message must name the empty block: "
                + ex.getMessage());
    }

    @Test
    public void utilWithoutMatcherRejected() {
        DynamicObject utils = new DynamicObject("utils");
        utils.addProp("broken", new DynamicObject("util"));
        DynamicObject model = patternModelWithUtils("demo/util-empty", "broken", utils);

        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(model));
        assertTrue(ex.getMessage().contains("demo/util-empty"));
        assertTrue(ex.getMessage().contains("broken"), "message must name the broken util: "
                + ex.getMessage());
    }

    @Test
    public void matchesWithoutUtilIdRejected() {
        DynamicObject utils = new DynamicObject("utils");
        utils.addProp("safe-close", utilWith(u -> u.addProp("pattern", "$X.close()")));
        DynamicObject model = patternModelWithUtils("demo/matches-blank", "safe-close", utils);
        ruleOf(model).prop_set("matches", " ");

        NopLintException ex = assertThrows(NopLintException.class, () -> parser.parseRuleModel(model));
        // a blank text matcher is treated as absent by the XOR scan, so the
        // observable failure is the container's "no matcher" rejection — the
        // parser's matches-without-id branch is the defensive backstop for
        // non-blank-preserving transports
        assertTrue(ex.getMessage().contains("no matcher"), ex.getMessage());
    }

    // ==================== helpers ====================

    private DynamicObject ruleOf(DynamicObject model) {
        return (DynamicObject) model.prop_get("rule");
    }

    private DynamicObject patternModel(String id) {
        DynamicObject model = new DynamicObject("lint-rule");
        model.addProp("id", id);
        DynamicObject rule = new DynamicObject("rule");
        rule.addProp("pattern", "foo()");
        model.addProp("rule", rule);
        return model;
    }

    private DynamicObject patternModelWithUtils(String id, String ruleMatcherUtil,
                                                DynamicObject utils) {
        DynamicObject model = patternModel(id);
        model.addProp("utils", utils);
        // the container matcher is the matches reference under test
        DynamicObject rule = ruleOf(model);
        rule.obj_propValues().remove("pattern");
        rule.addProp("matches", ruleMatcherUtil);
        return model;
    }

    private DynamicObject utilsWithMatcher(String utilId,
                                           java.util.function.Consumer<DynamicObject> configurer) {
        DynamicObject utils = new DynamicObject("utils");
        utils.addProp(utilId, utilWith(configurer));
        return utils;
    }

    private DynamicObject utilWith(java.util.function.Consumer<DynamicObject> configurer) {
        DynamicObject util = new DynamicObject("util");
        configurer.accept(util);
        return util;
    }

    private DynamicObject matcherWith(java.util.function.Consumer<DynamicObject> configurer) {
        DynamicObject matcher = new DynamicObject("matcher");
        configurer.accept(matcher);
        return matcher;
    }
}
