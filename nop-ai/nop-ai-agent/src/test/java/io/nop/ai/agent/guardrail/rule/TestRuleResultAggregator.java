package io.nop.ai.agent.guardrail.rule;

import io.nop.ai.agent.guardrail.GuardrailDirection;
import io.nop.ai.agent.guardrail.GuardrailMode;
import io.nop.ai.agent.guardrail.GuardrailResult;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RuleResultAggregator} covering Decision E: Block priority,
 * Modify chaining, conflict (Block wins), and the three GuardrailMode states.
 */
public class TestRuleResultAggregator {

    private static GuardrailRule block(String id, String pattern, String threatClass) {
        return new GuardrailRule(id, null, pattern, RuleAction.BLOCK, null, null, null, threatClass, null);
    }

    private static GuardrailRule modify(String id, String pattern, String replacement) {
        return new GuardrailRule(id, null, pattern, RuleAction.MODIFY, replacement, null, null, null, null);
    }

    private static GuardrailRuleSet setOf(GuardrailRule... rules) {
        return new GuardrailRuleSet("s", Arrays.asList(rules));
    }

    private static GuardrailResult aggregate(GuardrailMode mode, GuardrailRuleSet rs,
                                             List<String> active, String content) {
        return new RuleResultAggregator(mode).aggregate(rs, active, GuardrailDirection.INPUT, content);
    }

    @Test
    void allPassYieldsPass() {
        GuardrailRuleSet rs = setOf(block("a", "nomatch", "T_a"));
        GuardrailResult result = aggregate(GuardrailMode.ENFORCE, rs,
                Collections.singletonList("a"), "hello world");
        assertTrue(result.isPass());
    }

    @Test
    void blockPriorityWhenAnyActiveRuleMatchesBlock() {
        GuardrailRuleSet rs = setOf(
                block("a", "secret", "T_a"),
                block("b", "world", "T_b"));
        // both active, both match -> Block with concatenated reason
        GuardrailResult result = aggregate(GuardrailMode.ENFORCE, rs,
                Arrays.asList("a", "b"), "hello secret world");
        assertTrue(result.isBlock());
        String reason = ((GuardrailResult.BlockResult) result).getReason();
        assertTrue(reason.contains("T_a"));
        assertTrue(reason.contains("T_b"));
    }

    @Test
    void blockWinsOverModifyConflict() {
        GuardrailRuleSet rs = setOf(
                modify("m", "secret", "[REDACTED]"),
                block("b", "secret", "T_b"));
        GuardrailResult result = aggregate(GuardrailMode.ENFORCE, rs,
                Arrays.asList("m", "b"), "the secret value");
        assertTrue(result.isBlock(), "Block must win when both Block and Modify match the same content");
    }

    @Test
    void modifyChainingInDeclarationOrder() {
        // two modifies: first replaces "aa" with "X", second replaces "bb" with "Y"
        GuardrailRuleSet rs = setOf(
                modify("m1", "aa", "X"),
                modify("m2", "bb", "Y"));
        GuardrailResult result = aggregate(GuardrailMode.ENFORCE, rs,
                Arrays.asList("m1", "m2"), "aabb");
        assertTrue(result.isModify());
        assertEquals("XY", ((GuardrailResult.ModifyResult) result).getContent());
    }

    @Test
    void modifyChainingOrderFollowsRuleSetDeclarationNotActiveOrder() {
        // active order is m2,m1 but declaration is m1,m2; chaining follows declaration
        GuardrailRuleSet rs = setOf(
                modify("m1", "1", "one-"),
                modify("m2", "2", "two-"));
        // active list is m2,m1 — chaining still applies m1 then m2 (declaration order)
        GuardrailResult result = aggregate(GuardrailMode.ENFORCE, rs,
                Arrays.asList("m2", "m1"), "12");
        assertTrue(result.isModify());
        assertEquals("one-two-", ((GuardrailResult.ModifyResult) result).getContent());
    }

    @Test
    void modifyNoChangeYieldsPass() {
        // modify rule that does not match -> no modify applied
        GuardrailRuleSet rs = setOf(modify("m", "zzz", "[Z]"));
        GuardrailResult result = aggregate(GuardrailMode.ENFORCE, rs,
                Collections.singletonList("m"), "hello");
        assertTrue(result.isPass());
    }

    @Test
    void offModeShortCircuitsToPass() {
        GuardrailRuleSet rs = setOf(block("a", "secret", "T_a"));
        GuardrailResult result = aggregate(GuardrailMode.OFF, rs,
                Collections.singletonList("a"), "secret");
        assertTrue(result.isPass(), "OFF mode must skip all evaluation");
    }

    @Test
    void reportModeDowngradesBlockToPass() {
        GuardrailRuleSet rs = setOf(block("a", "secret", "T_a"));
        GuardrailResult result = aggregate(GuardrailMode.REPORT, rs,
                Collections.singletonList("a"), "secret");
        assertTrue(result.isPass(), "REPORT mode must downgrade a Block to Pass (logged)");
    }

    @Test
    void reportModeStillAppliesModify() {
        GuardrailRuleSet rs = setOf(modify("m", "secret", "[REDACTED]"));
        GuardrailResult result = aggregate(GuardrailMode.REPORT, rs,
                Collections.singletonList("m"), "a secret here");
        assertTrue(result.isModify(), "REPORT mode must still apply Modify (remediation, not reject)");
        assertEquals("a [REDACTED] here", ((GuardrailResult.ModifyResult) result).getContent());
    }

    @Test
    void nullContentShortCircuitsToPass() {
        GuardrailRuleSet rs = setOf(block("a", "secret", "T_a"));
        RuleResultAggregator agg = new RuleResultAggregator();
        assertTrue(agg.aggregate(rs, Collections.singletonList("a"), GuardrailDirection.INPUT, null).isPass());
        assertTrue(agg.aggregate(rs, Collections.singletonList("a"), GuardrailDirection.INPUT, "").isPass());
    }

    @Test
    void directionScopedRulesOnlyEvaluatedForTheirDirection() {
        // rule applies to OUTPUT only; aggregator called with INPUT -> no match
        GuardrailRule out = new GuardrailRule("out", GuardrailDirection.OUTPUT, "secret",
                RuleAction.BLOCK, null, null, null, "T_out", null);
        GuardrailRuleSet rs = setOf(out);
        RuleResultAggregator agg = new RuleResultAggregator();
        GuardrailResult inputResult = agg.aggregate(rs, Collections.singletonList("out"),
                GuardrailDirection.INPUT, "secret");
        assertTrue(inputResult.isPass(), "OUTPUT-scoped rule must not fire on INPUT");
        GuardrailResult outputResult = agg.aggregate(rs, Collections.singletonList("out"),
                GuardrailDirection.OUTPUT, "secret");
        assertTrue(outputResult.isBlock(), "OUTPUT-scoped rule must fire on OUTPUT");
    }

    @Test
    void emptyActiveYieldsPass() {
        GuardrailRuleSet rs = setOf(block("a", "secret", "T_a"));
        RuleResultAggregator agg = new RuleResultAggregator();
        assertTrue(agg.aggregate(rs, Collections.emptyList(), GuardrailDirection.INPUT, "secret").isPass());
        assertTrue(agg.aggregate(rs, null, GuardrailDirection.INPUT, "secret").isPass());
    }

    @Test
    void defaultModeIsEnforce() {
        assertEquals(GuardrailMode.ENFORCE, new RuleResultAggregator().getMode());
        // null mode -> ENFORCE
        assertEquals(GuardrailMode.ENFORCE, new RuleResultAggregator(null).getMode());
    }

    @Test
    void invalidPatternTreatedAsNoMatchFailSoft() {
        GuardrailRule bad = new GuardrailRule("bad", null, "[unclosed", RuleAction.BLOCK, null,
                null, null, "T_bad", null);
        GuardrailRuleSet rs = setOf(bad);
        GuardrailResult result = aggregate(GuardrailMode.ENFORCE, rs,
                Collections.singletonList("bad"), "anything");
        assertTrue(result.isPass(), "invalid pattern must not crash; treated as no-match");
        assertFalse(result.isBlock());
    }
}
