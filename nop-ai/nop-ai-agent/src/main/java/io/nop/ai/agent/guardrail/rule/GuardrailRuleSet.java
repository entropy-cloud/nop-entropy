package io.nop.ai.agent.guardrail.rule;

import io.nop.ai.agent.engine.NopAiAgentException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable, validated collection of {@link GuardrailRule}s (design
 * {@code guardrail-contract.md} §增量 2, Decision A /裁定 F).
 *
 * <p>Validation runs at construction (fail-loud) so an invalid rule set never
 * reaches the runtime guardrail:
 * <ul>
 * <li>rule ids are unique;</li>
 * <li>every {@code dependsOn}/{@code excludes} reference resolves to a rule in
 * the set;</li>
 * <li>the {@code dependsOn} graph is acyclic (cycle detection reuses
 * {@code io.nop.core.model.graph.dag.Dag.containsLoop()} — see
 * {@link DependsOnCycleDetector}).</li>
 * </ul>
 *
 * <p>Rules are kept in declaration order (stable for aggregation / Modify
 * chaining — Decision E).
 */
public final class GuardrailRuleSet {

    private final String id;
    private final List<GuardrailRule> rules;
    private final Map<String, GuardrailRule> byId;

    public GuardrailRuleSet(String id, List<GuardrailRule> rules) {
        if (id == null || id.isEmpty()) {
            throw new NopAiAgentException("GuardrailRuleSet: id must not be null or empty");
        }
        if (rules == null) {
            throw new NopAiAgentException("GuardrailRuleSet: rules must not be null (id=" + id + ")");
        }
        this.id = id;
        this.rules = Collections.unmodifiableList(new ArrayList<>(rules));
        Map<String, GuardrailRule> idx = new LinkedHashMap<>();
        for (GuardrailRule r : this.rules) {
            if (r == null) {
                throw new NopAiAgentException("GuardrailRuleSet: rule list contains null entry (id=" + id + ")");
            }
            if (idx.put(r.getId(), r) != null) {
                throw new NopAiAgentException(
                        "GuardrailRuleSet: duplicate rule id '" + r.getId() + "' (set=" + id + ")");
            }
        }
        this.byId = Collections.unmodifiableMap(idx);
        validateReferences();
        validateNoDependsOnCycle();
    }

    private void validateReferences() {
        for (GuardrailRule r : rules) {
            for (String dep : r.getDependsOn()) {
                if (!byId.containsKey(dep)) {
                    throw new NopAiAgentException("GuardrailRuleSet: rule '" + r.getId()
                            + "' dependsOn unknown rule '" + dep + "' (set=" + id + ")");
                }
            }
            for (String exc : r.getExcludes()) {
                if (!byId.containsKey(exc)) {
                    throw new NopAiAgentException("GuardrailRuleSet: rule '" + r.getId()
                            + "' excludes unknown rule '" + exc + "' (set=" + id + ")");
                }
            }
        }
    }

    private void validateNoDependsOnCycle() {
        List<List<String>> loopEdges = DependsOnCycleDetector.detectCycleEdges(rules);
        if (!loopEdges.isEmpty()) {
            throw new NopAiAgentException("GuardrailRuleSet: dependsOn graph contains a cycle (set="
                    + id + "). Offending edges: " + formatEdges(loopEdges)
                    + ". Cyclic dependencies are rejected at load time (fail-loud).");
        }
    }

    private static String formatEdges(List<List<String>> edges) {
        StringBuilder sb = new StringBuilder();
        for (List<String> e : edges) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(e.get(0)).append("->").append(e.get(1));
        }
        return sb.toString();
    }

    public String getId() {
        return id;
    }

    /**
     * Immutable rule list in declaration order.
     */
    public List<GuardrailRule> getRules() {
        return rules;
    }

    public GuardrailRule getRule(String ruleId) {
        return byId.get(ruleId);
    }

    public boolean contains(String ruleId) {
        return byId.containsKey(ruleId);
    }

    public int size() {
        return rules.size();
    }

    Map<String, GuardrailRule> getByIdIndex() {
        return byId;
    }

    /**
     * Resolve a list of rule ids to their {@link GuardrailRule}s in this set.
     * Throws if any id is unknown (defensive — references are validated at
     * construction, so this only fires for caller-side bugs).
     */
    public List<GuardrailRule> resolve(List<String> ruleIds) {
        if (ruleIds == null || ruleIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<GuardrailRule> out = new ArrayList<>(ruleIds.size());
        for (String rid : ruleIds) {
            GuardrailRule r = byId.get(rid);
            if (r == null) {
                throw new NopAiAgentException(
                        "GuardrailRuleSet: unknown rule id '" + rid + "' (set=" + id + ")");
            }
            out.add(r);
        }
        return out;
    }
}
