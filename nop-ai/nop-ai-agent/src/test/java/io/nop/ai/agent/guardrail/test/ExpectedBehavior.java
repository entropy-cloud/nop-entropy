package io.nop.ai.agent.guardrail.test;

/**
 * Expected guardrail verdict for an {@link AttackCase} (design
 * {@code guardrail-contract.md} §增量 1, Decision B).
 * <ul>
 * <li>{@link #BLOCK} — an attack payload that a correct guardrail must block.</li>
 * <li>{@link #PASS} — benign content that a correct guardrail must allow; used
 *     to measure false-positive rate.</li>
 * </ul>
 */
public enum ExpectedBehavior {
    BLOCK,
    PASS
}
