package io.nop.ai.agent.guardrail.test;

/**
 * Deterministic grader verdict for a single {@link AttackCase} (design
 * {@code guardrail-contract.md} §增量 1, Decision C).
 * <ul>
 * <li>{@link #PASS} — the guardrail behaved as expected.</li>
 * <li>{@link #FAIL} — the guardrail behaved opposite to expected (leak or
 *     false positive).</li>
 * <li>{@link #PARTIAL} — a {@code ModifyResult} half-pass: the guardrail
 *     neither cleanly blocked nor cleanly allowed.</li>
 * </ul>
 */
public enum Verdict {
    PASS,
    FAIL,
    PARTIAL
}
