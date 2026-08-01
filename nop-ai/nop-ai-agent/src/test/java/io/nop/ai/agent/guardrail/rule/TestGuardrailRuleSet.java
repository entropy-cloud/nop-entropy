package io.nop.ai.agent.guardrail.rule;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.guardrail.GuardrailDirection;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link GuardrailRuleSet} construction validation: id uniqueness,
 * dependsOn/excludes reference validity, and dependsOn acyclic check (fail-loud
 * via {@link DependsOnCycleDetector} reusing {@code Dag.containsLoop()}).
 */
public class TestGuardrailRuleSet {

    private static GuardrailRule rule(String id) {
        return new GuardrailRule(id, null, ".*", RuleAction.BLOCK, null, null, null, id, null);
    }

    @Test
    void acceptsValidRuleSet() {
        GuardrailRuleSet rs = new GuardrailRuleSet("s", Arrays.asList(
                new GuardrailRule("a", null, "x", RuleAction.BLOCK, null,
                        Collections.singletonList("b"), null, "T_a", "desc-a"),
                rule("b")));

        assertEquals("s", rs.getId());
        assertEquals(2, rs.size());
        assertEquals("a", rs.getRule("a").getId());
        assertTrue(rs.contains("a"));
        assertNotNull(rs.resolve(Collections.singletonList("a")));
    }

    @Test
    void rejectsNullId() {
        assertThrows(NopAiAgentException.class, () -> new GuardrailRuleSet(null, Collections.emptyList()));
        assertThrows(NopAiAgentException.class, () -> new GuardrailRuleSet("", Collections.emptyList()));
    }

    @Test
    void rejectsNullRules() {
        assertThrows(NopAiAgentException.class, () -> new GuardrailRuleSet("s", null));
    }

    @Test
    void rejectsNullEntryInRules() {
        assertThrows(NopAiAgentException.class, () -> new GuardrailRuleSet("s",
                Collections.singletonList(null)));
    }

    @Test
    void rejectsDuplicateRuleIds() {
        NopAiAgentException ex = assertThrows(NopAiAgentException.class, () -> new GuardrailRuleSet("s",
                Arrays.asList(rule("a"), rule("a"))));
        assertTrue(ex.getMessage().contains("duplicate rule id 'a'"));
    }

    @Test
    void rejectsUnknownDependsOnReference() {
        GuardrailRule r = new GuardrailRule("a", null, "x", RuleAction.BLOCK, null,
                Collections.singletonList("ghost"), null, null, null);
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> new GuardrailRuleSet("s", Collections.singletonList(r)));
        assertTrue(ex.getMessage().contains("dependsOn unknown rule 'ghost'"));
    }

    @Test
    void rejectsUnknownExcludesReference() {
        GuardrailRule r = new GuardrailRule("a", null, "x", RuleAction.BLOCK, null,
                null, Collections.singletonList("ghost"), null, null);
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> new GuardrailRuleSet("s", Collections.singletonList(r)));
        assertTrue(ex.getMessage().contains("excludes unknown rule 'ghost'"));
    }

    @Test
    void rejectsDependsOnSelfLoop() {
        GuardrailRule r = new GuardrailRule("a", null, "x", RuleAction.BLOCK, null,
                Collections.singletonList("a"), null, null, null);
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> new GuardrailRuleSet("s", Collections.singletonList(r)));
        assertTrue(ex.getMessage().contains("cycle"),
                "self-dependsOn must be detected as a cycle (fail-loud). Got: " + ex.getMessage());
    }

    @Test
    void rejectsDependsOnCycleTwoNodes() {
        GuardrailRule a = new GuardrailRule("a", null, "x", RuleAction.BLOCK, null,
                Collections.singletonList("b"), null, null, null);
        GuardrailRule b = new GuardrailRule("b", null, "x", RuleAction.BLOCK, null,
                Collections.singletonList("a"), null, null, null);
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> new GuardrailRuleSet("s", Arrays.asList(a, b)));
        assertTrue(ex.getMessage().contains("cycle"));
        // the offending edges should appear in the message for diagnosis
        assertTrue(ex.getMessage().contains("->") || ex.getMessage().contains("a"));
    }

    @Test
    void rejectsDependsOnCycleThreeNodes() {
        GuardrailRule a = new GuardrailRule("a", null, "x", RuleAction.BLOCK, null,
                Collections.singletonList("b"), null, null, null);
        GuardrailRule b = new GuardrailRule("b", null, "x", RuleAction.BLOCK, null,
                Collections.singletonList("c"), null, null, null);
        GuardrailRule c = new GuardrailRule("c", null, "x", RuleAction.BLOCK, null,
                Collections.singletonList("a"), null, null, null);
        assertThrows(NopAiAgentException.class,
                () -> new GuardrailRuleSet("s", Arrays.asList(a, b, c)));
    }

    @Test
    void allowsExcludesCycleBenign() {
        // excludes cycles are NOT detected (non-transitive, benign — Decision C)
        GuardrailRule a = new GuardrailRule("a", null, "x", RuleAction.BLOCK, null,
                null, Collections.singletonList("b"), null, null);
        GuardrailRule b = new GuardrailRule("b", null, "x", RuleAction.BLOCK, null,
                null, Collections.singletonList("a"), null, null);
        assertDoesNotThrow(() -> new GuardrailRuleSet("s", Arrays.asList(a, b)));
    }

    @Test
    void resolveRejectsUnknownId() {
        GuardrailRuleSet rs = new GuardrailRuleSet("s", Collections.singletonList(rule("a")));
        assertThrows(NopAiAgentException.class, () -> rs.resolve(Collections.singletonList("ghost")));
    }

    @Test
    void directionNullAppliesToBoth() {
        GuardrailRule r = rs_firstRuleWithNullDirection();
        assertTrue(r.appliesTo(GuardrailDirection.INPUT));
        assertTrue(r.appliesTo(GuardrailDirection.OUTPUT));
    }

    private GuardrailRule rs_firstRuleWithNullDirection() {
        return new GuardrailRule("a", null, "x", RuleAction.BLOCK, null, null, null, null, null);
    }

    @Test
    void directionScopedAppliesOnlyToThatDirection() {
        GuardrailRule r = new GuardrailRule("a", GuardrailDirection.OUTPUT, "x",
                RuleAction.BLOCK, null, null, null, null, null);
        assertTrue(r.appliesTo(GuardrailDirection.OUTPUT));
        assertEquals(false, r.appliesTo(GuardrailDirection.INPUT));
    }
}
