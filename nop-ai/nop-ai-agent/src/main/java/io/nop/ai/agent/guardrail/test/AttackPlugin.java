package io.nop.ai.agent.guardrail.test;

import java.util.List;

/**
 * Declarative corpus provider for the guardrail test suite (design
 * {@code guardrail-contract.md} §增量 1, Decision B). A plugin returns a static
 * list of {@link AttackCase}s — deterministic, repeatable regression data.
 * Dynamic LLM-driven attack generation is a successor and is intentionally out
 * of scope.
 */
public interface AttackPlugin {

    /**
     * Stable plugin identifier (e.g. {@code "prompt-injection"}).
     */
    String name();

    /**
     * The declarative corpus this plugin contributes. Must be deterministic:
     * repeated calls return equivalent content.
     */
    List<AttackCase> cases();
}
