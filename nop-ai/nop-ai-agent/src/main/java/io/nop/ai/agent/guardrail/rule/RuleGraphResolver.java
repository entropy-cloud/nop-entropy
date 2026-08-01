package io.nop.ai.agent.guardrail.rule;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves the active rule set from an initial matched set through the
 * {@code dependsOn}/{@code excludes} relationship graph (design
 * {@code guardrail-contract.md} §增量 2, Decision B).
 *
 * <p>The resolution is a deterministic three-phase algorithm (no fixpoint
 * oscillation, terminates, excludes always wins):
 * <ol>
 * <li><b>expand</b> — {@code dependsOn} transitive closure of the initial
 * matched set (monotonic, purely additive; BFS over dependsOn edges). This is
 * the maximal candidate set. Pulled-in rules participate in their own content
 * judgment (Decision B-2 — extends the evaluation surface).</li>
 * <li><b>collect excludes</b> — union of {@code r.excludes} for every rule
 * {@code r} in the expanded set (pulled-in rules' excludes cascade — Decision
 * B-5).</li>
 * <li><b>subtract</b> — {@code active = expanded − excluded}. Excludes wins
 * over dependsOn (Decision B-6); excludes applies to initial-matched members
 * too (Decision B-3); excludes is non-transitive (Decision B-4).</li>
 * </ol>
 *
 * <p>The output is deterministic: the same initial matched set resolves to the
 * same active set (assertable). dependsOn cycles are rejected upstream by
 * {@link GuardrailRuleSet} (fail-loud), so the closure terminates.
 */
public final class RuleGraphResolver {

    private final GuardrailRuleSet ruleSet;

    public RuleGraphResolver(GuardrailRuleSet ruleSet) {
        if (ruleSet == null) {
            throw new IllegalArgumentException("RuleGraphResolver: ruleSet must not be null");
        }
        this.ruleSet = ruleSet;
    }

    public GuardrailRuleSet getRuleSet() {
        return ruleSet;
    }

    /**
     * @param matchedRuleIds the initial matched rule ids (rules whose pattern
     *                       matched the content and whose direction applies).
     *                       Null/empty yields an empty active set.
     * @return the resolved active rule ids (deterministic order: expanded-set
     *         insertion order minus excluded — see implementation note).
     */
    public Set<String> resolve(Set<String> matchedRuleIds) {
        if (matchedRuleIds == null || matchedRuleIds.isEmpty()) {
            return Collections.emptySet();
        }

        // Phase 1: dependsOn transitive closure (BFS, monotonic). The expanded
        // set is ordered by first-encounter to keep resolution deterministic.
        LinkedHashSet<String> expanded = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        for (String id : matchedRuleIds) {
            if (ruleSet.contains(id) && expanded.add(id)) {
                queue.add(id);
            }
        }
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            GuardrailRule r = ruleSet.getRule(cur);
            if (r == null) {
                continue;
            }
            for (String dep : r.getDependsOn()) {
                if (expanded.add(dep)) {
                    queue.add(dep);
                }
            }
        }

        // Phase 2: union of excludes declared by every rule in the expanded set.
        Set<String> excluded = new LinkedHashSet<>();
        for (String id : expanded) {
            GuardrailRule r = ruleSet.getRule(id);
            if (r != null) {
                excluded.addAll(r.getExcludes());
            }
        }

        // Phase 3: subtract. Excludes wins (a rule excluded by any expanded
        // member is removed even if also depended-on).
        List<String> active = new ArrayList<>(expanded);
        active.removeAll(excluded);
        return new LinkedHashSet<>(active);
    }
}
