package io.nop.ai.agent.guardrail.rule;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.guardrail.GuardrailDirection;
import io.nop.ai.agent.guardrail.GuardrailMode;
import io.nop.ai.agent.guardrail.GuardrailResult;
import io.nop.ai.agent.guardrail.IContentGuardrail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Composable {@link IContentGuardrail} driven by the rule relationship graph
 * (design {@code guardrail-contract.md} §增量 2, Decision D). Holds a
 * {@link GuardrailRuleSet}, a {@link RuleGraphResolver}, and a
 * {@link RuleResultAggregator}.
 *
 * <p>{@link #check} flow:
 * <ol>
 * <li><b>seed</b> — initial matched set = rules whose {@code direction} applies
 * and whose {@code pattern} matches {@code content}.</li>
 * <li><b>resolve</b> — {@link RuleGraphResolver#resolve} expands the seed via
 * {@code dependsOn} (transitive) and narrows via {@code excludes} (excludes
 * wins). The active set is a pure rule-id set (structural; direction is NOT
 * filtered during resolution — pulled-in rules' excludes still take effect).</li>
 * <li><b>evaluate + aggregate</b> — {@link RuleResultAggregator#aggregate}
 * evaluates each active rule (direction filter + match) and aggregates to a
 * single {@link GuardrailResult} (Block priority / Modify chaining).</li>
 * </ol>
 *
 * <p><b>Coexists, does not replace</b> (Non-Goals / zero-regression): this is
 * an opt-in {@link IContentGuardrail} implementation. The shipped engine
 * default remains {@code NoOpContentGuardrail}; {@code PromptInjectionGuardrail}
 * is untouched. When not wired, behavior equals today.
 */
public class RuleGraphGuardrail implements IContentGuardrail {

    static final Logger LOG = LoggerFactory.getLogger(RuleGraphGuardrail.class);

    private final GuardrailRuleSet ruleSet;
    private final RuleGraphResolver resolver;
    private final RuleResultAggregator aggregator;

    public RuleGraphGuardrail(GuardrailRuleSet ruleSet) {
        this(ruleSet, GuardrailMode.ENFORCE);
    }

    public RuleGraphGuardrail(GuardrailRuleSet ruleSet, GuardrailMode mode) {
        if (ruleSet == null) {
            throw new IllegalArgumentException("RuleGraphGuardrail: ruleSet must not be null");
        }
        this.ruleSet = ruleSet;
        this.resolver = new RuleGraphResolver(ruleSet);
        this.aggregator = new RuleResultAggregator(mode);
    }

    public GuardrailRuleSet getRuleSet() {
        return ruleSet;
    }

    public RuleGraphResolver getResolver() {
        return resolver;
    }

    public RuleResultAggregator getAggregator() {
        return aggregator;
    }

    public GuardrailMode getMode() {
        return aggregator.getMode();
    }

    @Override
    public GuardrailResult check(GuardrailDirection direction, String content, AgentExecutionContext ctx) {
        if (direction == null) {
            direction = GuardrailDirection.INPUT;
        }
        if (aggregator.getMode() == GuardrailMode.OFF || content == null || content.isEmpty()) {
            return GuardrailResult.PassResult.instance();
        }

        // Step 1: seed — rules applicable to this direction whose pattern matches
        Set<String> matched = seed(direction, content);
        if (matched.isEmpty()) {
            return GuardrailResult.PassResult.instance();
        }

        // Step 2: resolve the active set through the relationship graph
        Set<String> active = resolver.resolve(matched);
        if (active.isEmpty()) {
            return GuardrailResult.PassResult.instance();
        }

        // Step 3: evaluate each active rule (direction filter + match) and aggregate
        return aggregator.aggregate(ruleSet, new ArrayList<>(active), direction, content);
    }

    private Set<String> seed(GuardrailDirection direction, String content) {
        Set<String> matched = new LinkedHashSet<>();
        for (GuardrailRule r : ruleSet.getRules()) {
            if (!r.appliesTo(direction)) {
                continue;
            }
            if (matches(r, content)) {
                matched.add(r.getId());
            }
        }
        return matched;
    }

    private static boolean matches(GuardrailRule r, String content) {
        try {
            return Pattern.compile(r.getPattern()).matcher(content).find();
        } catch (Exception e) {
            LOG.warn("RuleGraphGuardrail: invalid pattern for rule '{}'; treating as no-match", r.getId(), e);
            return false;
        }
    }
}
