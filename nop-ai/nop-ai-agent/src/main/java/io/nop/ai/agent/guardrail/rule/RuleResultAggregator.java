package io.nop.ai.agent.guardrail.rule;

import io.nop.ai.agent.guardrail.GuardrailMode;
import io.nop.ai.agent.guardrail.GuardrailResult;
import io.nop.ai.agent.guardrail.GuardrailDirection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Aggregates per-rule evaluation results into a single
 * {@link GuardrailResult} (design {@code guardrail-contract.md} §增量 2,
 * Decision E).
 *
 * <p>Aggregation policy:
 * <ul>
 * <li><b>Block priority</b> — if any active rule matches and is
 * {@link RuleAction#BLOCK}, the aggregate is a {@link GuardrailResult.BlockResult}
 * (reason concatenates every matched-block rule's threat class / id for
 * observability). Block also wins over concurrent Modify (safety-first).</li>
 * <li><b>Modify chaining</b> — if no block matched and one or more
 * {@link RuleAction#MODIFY} rules matched, modifies are applied in rule-set
 * declaration order (each modify acts on the previous output), yielding a
 * {@link GuardrailResult.ModifyResult}.</li>
 * <li><b>All pass</b> — otherwise a {@link GuardrailResult.PassResult}.</li>
 * </ul>
 *
 * <p>The {@link GuardrailMode} semantics mirror {@code PromptInjectionGuardrail}:
 * {@code OFF} skips everything; {@code REPORT} logs and downgrades a Block to
 * Pass (Modify still applies); {@code ENFORCE} applies the policy above.
 */
public final class RuleResultAggregator {

    static final Logger LOG = LoggerFactory.getLogger(RuleResultAggregator.class);

    private final GuardrailMode mode;

    public RuleResultAggregator() {
        this(GuardrailMode.ENFORCE);
    }

    public RuleResultAggregator(GuardrailMode mode) {
        this.mode = mode != null ? mode : GuardrailMode.ENFORCE;
    }

    public GuardrailMode getMode() {
        return mode;
    }

    /**
     * Evaluate the active rules against {@code content} and aggregate.
     *
     * @param ruleSet      the rule set (declaration order governs Modify chaining)
     * @param activeRuleIds the resolved active rule ids (from {@link RuleGraphResolver})
     * @param direction    the direction under evaluation (used only for logging)
     * @param content      the content to evaluate; null/empty short-circuits to Pass
     * @return the aggregated {@link GuardrailResult}
     */
    public GuardrailResult aggregate(GuardrailRuleSet ruleSet, List<String> activeRuleIds,
                                     GuardrailDirection direction, String content) {
        if (mode == GuardrailMode.OFF || content == null || content.isEmpty()) {
            return GuardrailResult.PassResult.instance();
        }
        if (ruleSet == null || activeRuleIds == null || activeRuleIds.isEmpty()) {
            return GuardrailResult.PassResult.instance();
        }

        List<GuardrailRule> matchedBlock = new ArrayList<>();
        List<GuardrailRule> matchedModify = new ArrayList<>();

        for (String rid : activeRuleIds) {
            GuardrailRule r = ruleSet.getRule(rid);
            if (r == null) {
                continue;
            }
            if (!r.appliesTo(direction)) {
                continue;
            }
            if (!matches(r, content)) {
                continue;
            }
            if (r.getAction() == RuleAction.BLOCK) {
                matchedBlock.add(r);
            } else {
                matchedModify.add(r);
            }
        }

        if (!matchedBlock.isEmpty()) {
            String reason = buildBlockReason(matchedBlock);
            if (mode == GuardrailMode.REPORT) {
                LOG.warn("RuleResultAggregator[REPORT]: block downgraded to pass. reason={} direction={}",
                        reason, direction);
                // apply Modify anyway? REPORT keeps block as pass; if there are
                // concurrent modifies they are ignored (block is the signal).
                return GuardrailResult.PassResult.instance();
            }
            return new GuardrailResult.BlockResult(reason);
        }

        if (!matchedModify.isEmpty()) {
            // chain modifies in declaration order (ruleSet.getRules() order)
            String current = content;
            for (GuardrailRule r : ruleSet.getRules()) {
                if (!matchedModify.contains(r)) {
                    continue;
                }
                current = applyModify(r, current);
            }
            // only return Modify if content actually changed
            if (!current.equals(content)) {
                return new GuardrailResult.ModifyResult(current);
            }
            return GuardrailResult.PassResult.instance();
        }

        return GuardrailResult.PassResult.instance();
    }

    private static boolean matches(GuardrailRule r, String content) {
        try {
            return Pattern.compile(r.getPattern()).matcher(content).find();
        } catch (Exception e) {
            LOG.warn("RuleResultAggregator: invalid pattern for rule '{}'; treating as no-match", r.getId(), e);
            return false;
        }
    }

    private static String applyModify(GuardrailRule r, String content) {
        try {
            return Pattern.compile(r.getPattern()).matcher(content).replaceAll(
                    r.getModifyReplacement() != null ? r.getModifyReplacement() : "");
        } catch (Exception e) {
            LOG.warn("RuleResultAggregator: modify replaceAll failed for rule '{}'; returning content unchanged",
                    r.getId(), e);
            return content;
        }
    }

    private static String buildBlockReason(List<GuardrailRule> blockRules) {
        List<String> parts = new ArrayList<>();
        for (GuardrailRule r : blockRules) {
            String tag = r.getThreatClass() != null ? r.getThreatClass() : r.getId();
            parts.add(tag);
        }
        return "rule graph blocked (" + String.join(", ", parts) + ")";
    }
}
