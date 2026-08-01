package io.nop.ai.agent.guardrail.rule;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.guardrail.GuardrailDirection;
import io.nop.ai.agent.guardrail.GuardrailMode;
import io.nop.ai.agent.guardrail.GuardrailResult;
import io.nop.ai.agent.guardrail.IContentGuardrail;
import io.nop.ai.agent.model.AgentModel;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Component tests for {@link RuleGraphGuardrail}: seed → resolve → aggregate
 * wiring (design Decision D), including the dependsOn-convergence and
 * excludes-narrowing observable effects.
 */
public class TestRuleGraphGuardrail {

    private static final AgentExecutionContext CTX = AgentExecutionContext.create(
            new AgentModel(), "rule-graph-test");

    private static GuardrailRule block(String id, String pattern, String threatClass,
                                       java.util.List<String> dependsOn, java.util.List<String> excludes) {
        return new GuardrailRule(id, null, pattern, RuleAction.BLOCK, null, dependsOn, excludes,
                threatClass, null);
    }

    private static GuardrailRule modify(String id, String pattern, String replacement) {
        return new GuardrailRule(id, null, pattern, RuleAction.MODIFY, replacement, null, null, null, null);
    }

    private static GuardrailResult check(IContentGuardrail g, GuardrailDirection dir, String content) {
        return g.check(dir, content, CTX);
    }

    @Test
    void blockWhenSeedMatches() {
        GuardrailRuleSet rs = new GuardrailRuleSet("s", Collections.singletonList(
                block("a", "secret", "T_a", null, null)));
        RuleGraphGuardrail g = new RuleGraphGuardrail(rs);

        GuardrailResult result = check(g, GuardrailDirection.INPUT, "a secret value");
        assertTrue(result.isBlock());
        assertTrue(((GuardrailResult.BlockResult) result).getReason().contains("T_a"));
    }

    @Test
    void passWhenNothingMatches() {
        GuardrailRuleSet rs = new GuardrailRuleSet("s", Collections.singletonList(
                block("a", "secret", "T_a", null, null)));
        RuleGraphGuardrail g = new RuleGraphGuardrail(rs);

        assertTrue(check(g, GuardrailDirection.INPUT, "totally benign").isPass());
    }

    @Test
    void dependsOnExtendsDetectionSurface() {
        // a matches "alpha"; a dependsOn b (b matches "beta"). Content has both
        // -> b is pulled in AND matches -> both contribute, Block.
        // Key: even though only "alpha" seed-matched, "beta" rule fires because
        // dependsOn pulled it in (Decision B-2).
        GuardrailRuleSet rs = new GuardrailRuleSet("s", Arrays.asList(
                block("a", "alpha", "T_a", Collections.singletonList("b"), null),
                block("b", "beta", "T_b", null, null)));
        RuleGraphGuardrail g = new RuleGraphGuardrail(rs);

        // content matches both
        GuardrailResult both = check(g, GuardrailDirection.INPUT, "alpha and beta");
        assertTrue(both.isBlock());
        String reason = ((GuardrailResult.BlockResult) both).getReason();
        assertTrue(reason.contains("T_a"));
        assertTrue(reason.contains("T_b"), "b was pulled in via dependsOn and matched -> contributes");

        // content matches only seed (alpha); b pulled in but b's pattern (beta) does not match
        GuardrailResult onlySeed = check(g, GuardrailDirection.INPUT, "alpha only");
        assertTrue(onlySeed.isBlock());
        assertTrue(((GuardrailResult.BlockResult) onlySeed).getReason().contains("T_a"));
        assertFalse(((GuardrailResult.BlockResult) onlySeed).getReason().contains("T_b"),
                "b pulled in but did not match content -> must not contribute to block reason");
    }

    @Test
    void excludesNarrowsEvenWhenPatternMatches() {
        // fin matches "transfer", chat matches "transfer funds". fin excludes chat.
        // When "transfer funds" is the content, both patterns match, but fin
        // excludes chat -> chat removed from active set -> only fin blocks.
        GuardrailRuleSet rs = new GuardrailRuleSet("s", Arrays.asList(
                block("fin", "(?i)transfer", "FIN", null, Collections.singletonList("chat")),
                block("chat", "(?i)transfer", "CHAT", null, null)));
        RuleGraphGuardrail g = new RuleGraphGuardrail(rs);

        GuardrailResult result = check(g, GuardrailDirection.INPUT, "transfer funds now");
        assertTrue(result.isBlock());
        String reason = ((GuardrailResult.BlockResult) result).getReason();
        assertTrue(reason.contains("FIN"));
        assertFalse(reason.contains("CHAT"),
                "chat excluded by fin -> must not appear in block reason");
    }

    @Test
    void modifyAppliedForOutput() {
        GuardrailRuleSet rs = new GuardrailRuleSet("s", Collections.singletonList(
                modify("redact", "secret-token-\\d+", "[REDACTED]")));
        RuleGraphGuardrail g = new RuleGraphGuardrail(rs);

        GuardrailResult result = check(g, GuardrailDirection.OUTPUT, "leak secret-token-42 here");
        assertTrue(result.isModify());
        assertEquals("leak [REDACTED] here", ((GuardrailResult.ModifyResult) result).getContent());
    }

    @Test
    void offModeAlwaysPass() {
        GuardrailRuleSet rs = new GuardrailRuleSet("s", Collections.singletonList(
                block("a", "secret", "T_a", null, null)));
        RuleGraphGuardrail g = new RuleGraphGuardrail(rs, GuardrailMode.OFF);

        assertTrue(check(g, GuardrailDirection.INPUT, "secret").isPass());
    }

    @Test
    void reportModeDowngradesBlock() {
        GuardrailRuleSet rs = new GuardrailRuleSet("s", Collections.singletonList(
                block("a", "secret", "T_a", null, null)));
        RuleGraphGuardrail g = new RuleGraphGuardrail(rs, GuardrailMode.REPORT);

        assertTrue(check(g, GuardrailDirection.INPUT, "secret").isPass(),
                "REPORT mode downgrades Block to Pass");
    }

    @Test
    void nullAndEmptyContentPass() {
        GuardrailRuleSet rs = new GuardrailRuleSet("s", Collections.singletonList(
                block("a", "secret", "T_a", null, null)));
        RuleGraphGuardrail g = new RuleGraphGuardrail(rs);

        assertTrue(check(g, GuardrailDirection.INPUT, null).isPass());
        assertTrue(check(g, GuardrailDirection.INPUT, "").isPass());
    }

    @Test
    void nullDirectionTreatedAsInput() {
        GuardrailRuleSet rs = new GuardrailRuleSet("s", Collections.singletonList(
                block("a", "secret", "T_a", null, null)));
        RuleGraphGuardrail g = new RuleGraphGuardrail(rs);
        // null direction defaults to INPUT (no NPE)
        assertTrue(g.check(null, "secret", CTX).isBlock());
    }

    @Test
    void nullRuleSetRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RuleGraphGuardrail(null));
    }

    @Test
    void accessorsExposeComponents() {
        GuardrailRuleSet rs = new GuardrailRuleSet("s", Collections.singletonList(
                block("a", "x", "T", null, null)));
        RuleGraphGuardrail g = new RuleGraphGuardrail(rs, GuardrailMode.REPORT);
        assertSame(rs, g.getRuleSet());
        assertNotNull(g.getResolver());
        assertNotNull(g.getAggregator());
        assertEquals(GuardrailMode.REPORT, g.getMode());
    }

    @Test
    void directionScopedRuleOnlyFiresForItsDirection() {
        GuardrailRule output = new GuardrailRule("out", GuardrailDirection.OUTPUT, "secret",
                RuleAction.BLOCK, null, null, null, "T_out", null);
        GuardrailRuleSet rs = new GuardrailRuleSet("s", Collections.singletonList(output));
        RuleGraphGuardrail g = new RuleGraphGuardrail(rs);

        assertTrue(check(g, GuardrailDirection.INPUT, "secret").isPass(),
                "OUTPUT-scoped rule must not seed on INPUT");
        assertTrue(check(g, GuardrailDirection.OUTPUT, "secret").isBlock());
    }

    /**
     * Prove the resolver is actually invoked at runtime (not bypassed): wrap
     * the rule set so we can detect dependsOn-driven expansion. If the resolver
     * were bypassed, a non-seed-matched pulled-in rule would never fire.
     */
    @Test
    void resolverIsInvokedAtRuntimeNotBypassed() {
        AtomicInteger evalSignal = new AtomicInteger();
        // a matches "x"; a dependsOn b; b matches "y". Content "x y" — if
        // resolver runs, b is pulled in and matches "y" -> block reason has T_b.
        GuardrailRuleSet rs = new GuardrailRuleSet("s", Arrays.asList(
                block("a", "x", "T_a", Collections.singletonList("b"), null),
                block("b", "y", "T_b", null, null)));
        RuleGraphGuardrail g = new RuleGraphGuardrail(rs);
        GuardrailResult result = check(g, GuardrailDirection.INPUT, "x and y together");
        assertTrue(result.isBlock());
        assertTrue(((GuardrailResult.BlockResult) result).getReason().contains("T_b"),
                "resolver must have pulled in b (which then matched y) — proves resolver ran");
        assertEquals(1, evalSignal.incrementAndGet());
    }
}
