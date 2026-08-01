package io.nop.ai.agent.guardrail.rule;

/**
 * The result action a {@link GuardrailRule} takes when its pattern matches
 * (design {@code guardrail-contract.md} §增量 2, Decision A).
 *
 * <ul>
 * <li>{@link #BLOCK} — the rule produces a {@code BlockResult} (safety-first
 * hard reject).</li>
 * <li>{@link #MODIFY} — the rule produces a {@code ModifyResult} with
 * {@link GuardrailRule#getModifyReplacement()} as the sanitized content
 * (remediation, not a reject).</li>
 * </ul>
 */
public enum RuleAction {
    BLOCK,
    MODIFY
}
