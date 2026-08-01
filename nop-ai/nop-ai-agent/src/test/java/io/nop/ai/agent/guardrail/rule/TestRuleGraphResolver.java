package io.nop.ai.agent.guardrail.rule;

import io.nop.ai.agent.guardrail.GuardrailDirection;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link RuleGraphResolver} covering every clause of Decision B
 * (the relationship-semantics checklist).
 */
public class TestRuleGraphResolver {

    private static GuardrailRule rule(String id, List<String> dependsOn, List<String> excludes) {
        return new GuardrailRule(id, null, ".*", RuleAction.BLOCK, null, dependsOn, excludes, id, null);
    }

    private static Set<String> set(String... ids) {
        return new LinkedHashSet<>(Arrays.asList(ids));
    }

    private static Set<String> resolve(GuardrailRuleSet rs, String... matched) {
        return new RuleGraphResolver(rs).resolve(set(matched));
    }

    // Decision B-1: dependsOn is transitive closure
    @Test
    void dependsOnIsTransitiveClosure() {
        GuardrailRuleSet rs = new GuardrailRuleSet("s", Arrays.asList(
                rule("A", Collections.singletonList("B"), null),
                rule("B", Collections.singletonList("C"), null),
                rule("C", null, null)));

        Set<String> active = resolve(rs, "A");

        assertEquals(set("A", "B", "C"), active,
                "dependsOn must be transitive: hitting A pulls in B and C");
    }

    // Decision B-2: pulled-in rules participate in their own evaluation (this is
    // the resolver contract: the active set simply contains rule ids, all of
    // which are evaluated downstream).
    @Test
    void pulledInRulesArePartOfActiveSetForEvaluation() {
        GuardrailRuleSet rs = new GuardrailRuleSet("s", Arrays.asList(
                rule("A", Collections.singletonList("B"), null),
                rule("B", null, null)));

        Set<String> active = resolve(rs, "A");

        assertTrue(active.contains("B"),
                "B is pulled in and remains in the active set so it is evaluated against content");
    }

    // Decision B-3: excludes applies to initial-matched members too
    @Test
    void excludesRemovesInitialMatchedMembers() {
        GuardrailRuleSet rs = new GuardrailRuleSet("s", Arrays.asList(
                rule("A", null, Collections.singletonList("B")),
                rule("B", null, null)));

        // both A and B are matched by content; A excludes B -> B removed even
        // though it was content-matched
        Set<String> active = resolve(rs, "A", "B");

        assertFalse(active.contains("B"),
                "excludes must remove a content-matched rule (structural narrowing overrides match)");
        assertTrue(active.contains("A"));
    }

    // Decision B-4: excludes is NOT transitive
    @Test
    void excludesIsNotTransitive() {
        // A excludes B, B excludes C. Hitting A removes B only; C is untouched.
        GuardrailRuleSet rs = new GuardrailRuleSet("s", Arrays.asList(
                rule("A", null, Collections.singletonList("B")),
                rule("B", null, Collections.singletonList("C")),
                rule("C", null, null)));

        Set<String> active = resolve(rs, "A");

        assertFalse(active.contains("B"), "direct excludes removes B");
        // C is not indirectly excluded (no transitivity), and C is reachable
        // only if pulled in; A does not depend on C, so C is simply absent.
        assertFalse(active.contains("C"));
        assertEquals(set("A"), active);
    }

    // Decision B-4 (positive): excludes does not ripple along excludes edges
    @Test
    void excludesChainDoesNotRemoveIndirectTargets() {
        // A depends on B (so B is pulled in); B excludes C (but C is not in the
        // set); C excludes D. Nothing pulls D in, so D is absent. The point:
        // B's excludes only removes C if C were in expanded; excludes edges are
        // not followed as a closure.
        GuardrailRuleSet rs = new GuardrailRuleSet("s", Arrays.asList(
                rule("A", Collections.singletonList("B"), null),
                rule("B", null, Collections.singletonList("C")),
                rule("C", null, Collections.singletonList("D")),
                rule("D", null, null)));

        Set<String> active = resolve(rs, "A");

        assertEquals(set("A", "B"), active);
    }

    // Decision B-5: pulled-in rules' own excludes cascade into effect
    @Test
    void pulledInRulesExcludesCascade() {
        // A depends on B and C (both pulled in); C excludes B. So expanded =
        // {A,B,C}, excluded = {B} (from C), active = {A,C}.
        GuardrailRuleSet rs = new GuardrailRuleSet("s", Arrays.asList(
                rule("A", Arrays.asList("B", "C"), null),
                rule("B", null, null),
                rule("C", null, Collections.singletonList("B"))));

        Set<String> active = resolve(rs, "A");

        assertEquals(set("A", "C"), active,
                "a pulled-in rule's own excludes must take effect (cascade)");
    }

    // Decision B-6: excludes wins over dependsOn
    @Test
    void excludesWinsOverDependsOn() {
        // A depends on B (B should be pulled in); A also excludes B (B should
        // be removed). Excludes wins.
        GuardrailRuleSet rs = new GuardrailRuleSet("s", Arrays.asList(
                rule("A", Collections.singletonList("B"), Collections.singletonList("B")),
                rule("B", null, null)));

        Set<String> active = resolve(rs, "A");

        assertFalse(active.contains("B"),
                "excludes must win over dependsOn when both target the same rule");
        assertEquals(set("A"), active);
    }

    // Determinism: same matched set -> same active set
    @Test
    void resolutionIsDeterministic() {
        GuardrailRuleSet rs = new GuardrailRuleSet("s", Arrays.asList(
                rule("A", Collections.singletonList("B"), Collections.singletonList("C")),
                rule("B", null, null),
                rule("C", null, null),
                rule("D", Collections.singletonList("A"), null)));

        Set<String> first = resolve(rs, "D");
        Set<String> second = resolve(rs, "D");

        assertEquals(first, second, "same matched set must resolve to the same active set");
        assertEquals(set("D", "A", "B"), first,
                "D->A->B pulled in, A excludes C (C not in set anyway)");
    }

    @Test
    void emptyMatchedYieldsEmptyActive() {
        GuardrailRuleSet rs = new GuardrailRuleSet("s", Collections.singletonList(rule("A", null, null)));
        assertEquals(Collections.emptySet(), new RuleGraphResolver(rs).resolve(Collections.emptySet()));
        assertEquals(Collections.emptySet(), new RuleGraphResolver(rs).resolve(null));
    }

    @Test
    void unknownMatchedIdsAreIgnored() {
        GuardrailRuleSet rs = new GuardrailRuleSet("s", Collections.singletonList(rule("A", null, null)));
        // "ghost" is not in the set; ignored (not an error — resolver is pure)
        Set<String> active = resolve(rs, "A", "ghost");
        assertEquals(set("A"), active);
    }

    // Enterprise-compliance-shaped scenario (end-to-end resolver behavior)
    @Test
    void enterpriseComplianceStructuralConvergence() {
        // fin-rule excludes general-chat (so when fin is active, general is gone)
        // fin-rule dependsOn audit (audit pulled in to extend detection surface)
        GuardrailRuleSet rs = new GuardrailRuleSet("compliance", Arrays.asList(
                rule("fin-transaction", Collections.singletonList("audit"),
                        Collections.singletonList("general-chat")),
                rule("general-chat", null, null),
                rule("audit", null, null)));

        Set<String> active = resolve(rs, "fin-transaction", "general-chat");
        assertTrue(active.contains("fin-transaction"));
        assertTrue(active.contains("audit"), "dependsOn pulled in audit");
        assertFalse(active.contains("general-chat"), "excludes removed general-chat");
    }
}
