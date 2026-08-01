package io.nop.ai.agent.guardrail.rule;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.guardrail.GuardrailDirection;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link GuardrailRule} immutable value object semantics.
 */
public class TestGuardrailRule {

    @Test
    void ctorRejectsInvalidArgs() {
        assertThrows(IllegalArgumentException.class, () ->
                new GuardrailRule(null, null, ".*", RuleAction.BLOCK, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () ->
                new GuardrailRule("", null, ".*", RuleAction.BLOCK, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () ->
                new GuardrailRule("a", null, null, RuleAction.BLOCK, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () ->
                new GuardrailRule("a", null, ".*", null, null, null, null, null, null));
        // MODIFY requires non-null replacement
        assertThrows(IllegalArgumentException.class, () ->
                new GuardrailRule("a", null, ".*", RuleAction.MODIFY, null, null, null, null, null));
    }

    @Test
    void modifyWithNonNullReplacementAccepted() {
        GuardrailRule r = new GuardrailRule("a", null, "secret", RuleAction.MODIFY,
                "[REDACTED]", null, null, null, null);
        assertEquals(RuleAction.MODIFY, r.getAction());
        assertEquals("[REDACTED]", r.getModifyReplacement());
    }

    @Test
    void dependsOnAndExcludesAreDefensivelyCopiedAndImmutable() {
        List<String> deps = new ArrayList<>();
        deps.add("b");
        List<String> excs = new ArrayList<>();
        excs.add("c");
        GuardrailRule r = new GuardrailRule("a", null, ".*", RuleAction.BLOCK, null, deps, excs, null, null);

        // mutate source lists after construction
        deps.add("x");
        excs.add("y");

        assertEquals(Collections.singletonList("b"), r.getDependsOn(),
                "dependsOn must be a defensive copy (source mutation must not leak)");
        assertEquals(Collections.singletonList("c"), r.getExcludes(),
                "excludes must be a defensive copy");

        // returned lists are immutable
        assertThrows(UnsupportedOperationException.class, () -> r.getDependsOn().add("z"));
        assertThrows(UnsupportedOperationException.class, () -> r.getExcludes().add("z"));
    }

    @Test
    void nullDependsOnAndExcludesReturnEmptyList() {
        GuardrailRule r = new GuardrailRule("a", null, ".*", RuleAction.BLOCK, null, null, null, null, null);
        assertTrue(r.getDependsOn().isEmpty());
        assertTrue(r.getExcludes().isEmpty());
        assertFalse(r.hasRelationships());
    }

    @Test
    void hasRelationshipsReflectsEdges() {
        GuardrailRule withDep = new GuardrailRule("a", null, ".*", RuleAction.BLOCK, null,
                Collections.singletonList("b"), null, null, null);
        GuardrailRule withExc = new GuardrailRule("c", null, ".*", RuleAction.BLOCK, null,
                null, Collections.singletonList("d"), null, null);
        assertTrue(withDep.hasRelationships());
        assertTrue(withExc.hasRelationships());
    }

    @Test
    void appliesToDirection() {
        GuardrailRule both = new GuardrailRule("a", null, ".*", RuleAction.BLOCK, null, null, null, null, null);
        GuardrailRule inputOnly = new GuardrailRule("b", GuardrailDirection.INPUT, ".*",
                RuleAction.BLOCK, null, null, null, null, null);
        assertTrue(both.appliesTo(GuardrailDirection.INPUT));
        assertTrue(both.appliesTo(GuardrailDirection.OUTPUT));
        assertTrue(inputOnly.appliesTo(GuardrailDirection.INPUT));
        assertFalse(inputOnly.appliesTo(GuardrailDirection.OUTPUT));
    }

    @Test
    void equalsHashCodeByAllFields() {
        GuardrailRule r1 = new GuardrailRule("a", GuardrailDirection.INPUT, "x", RuleAction.BLOCK,
                null, Collections.singletonList("b"), Collections.singletonList("c"), "T", "d");
        GuardrailRule r2 = new GuardrailRule("a", GuardrailDirection.INPUT, "x", RuleAction.BLOCK,
                null, Collections.singletonList("b"), Collections.singletonList("c"), "T", "d");
        GuardrailRule diff = new GuardrailRule("a", GuardrailDirection.INPUT, "x", RuleAction.BLOCK,
                null, Collections.singletonList("z"), Collections.singletonList("c"), "T", "d");

        assertEquals(r1, r2);
        assertEquals(r1.hashCode(), r2.hashCode());
        assertFalse(r1.equals(diff));
    }

    @Test
    void toStringContainsIdAndAction() {
        GuardrailRule r = new GuardrailRule("my-rule", null, ".*", RuleAction.BLOCK, null,
                Collections.singletonList("dep"), null, "THREAT", null);
        String s = r.toString();
        assertTrue(s.contains("my-rule"));
        assertTrue(s.contains("BLOCK"));
        assertTrue(s.contains("dep"));
        assertTrue(s.contains("THREAT"));
    }
}
