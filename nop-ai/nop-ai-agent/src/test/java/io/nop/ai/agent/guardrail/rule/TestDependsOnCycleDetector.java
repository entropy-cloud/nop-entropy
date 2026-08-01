package io.nop.ai.agent.guardrail.rule;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link DependsOnCycleDetector}, the bridge to the platform
 * {@code Dag.containsLoop()} capability.
 */
public class TestDependsOnCycleDetector {

    private static GuardrailRule rule(String id, List<String> dependsOn) {
        return new GuardrailRule(id, null, ".*", RuleAction.BLOCK, null, dependsOn, null, id, null);
    }

    private static boolean hasCycle(GuardrailRule... rules) {
        return !DependsOnCycleDetector.detectCycleEdges(Arrays.asList(rules)).isEmpty();
    }

    @Test
    void emptyAndNullHaveNoCycle() {
        assertTrue(DependsOnCycleDetector.detectCycleEdges(null).isEmpty());
        assertTrue(DependsOnCycleDetector.detectCycleEdges(Collections.emptyList()).isEmpty());
    }

    @Test
    void acyclicGraphHasNoCycle() {
        // A->B->C, plus isolated D, plus diamond A->C
        GuardrailRule a = rule("a", Collections.singletonList("b"));
        GuardrailRule b = rule("b", Collections.singletonList("c"));
        GuardrailRule c = rule("c", null);
        GuardrailRule d = rule("d", null);

        assertFalse(hasCycle(a, b, c, d));
    }

    @Test
    void selfLoopIsCycle() {
        GuardrailRule a = rule("a", Collections.singletonList("a"));
        assertTrue(hasCycle(a));
    }

    @Test
    void twoNodeCycle() {
        GuardrailRule a = rule("a", Collections.singletonList("b"));
        GuardrailRule b = rule("b", Collections.singletonList("a"));
        assertTrue(hasCycle(a, b));
    }

    @Test
    void threeNodeCycle() {
        GuardrailRule a = rule("a", Collections.singletonList("b"));
        GuardrailRule b = rule("b", Collections.singletonList("c"));
        GuardrailRule c = rule("c", Collections.singletonList("a"));
        assertTrue(hasCycle(a, b, c));
    }

    @Test
    void diamondNoCycle() {
        // a->b, a->c, b->d, c->d (diamond, acyclic)
        GuardrailRule a = rule("a", Arrays.asList("b", "c"));
        GuardrailRule b = rule("b", Collections.singletonList("d"));
        GuardrailRule c = rule("c", Collections.singletonList("d"));
        GuardrailRule d = rule("d", new ArrayList<>());
        assertFalse(hasCycle(a, b, c, d));
    }

    @Test
    void loopEdgesReportedForDiagnosis() {
        GuardrailRule a = rule("a", Collections.singletonList("b"));
        GuardrailRule b = rule("b", Collections.singletonList("a"));
        List<List<String>> edges = DependsOnCycleDetector.detectCycleEdges(Arrays.asList(a, b));
        assertFalse(edges.isEmpty(), "cycle must produce loop edges for diagnosis");
    }
}
