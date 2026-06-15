package io.nop.ai.agent.router;

/**
 * Request complexity tier used by {@link SmartModelRouter} to route requests to
 * different model tiers (design {@code nop-ai-agent-llm-layer.md} §6.3–6.4 /
 * plan 209). A functional router classifies each request into one of three
 * tiers via observable heuristics (message length, tool count, code/structured
 * content) and selects the model configured for that tier.
 *
 * <p>The {@link #getLevel() level} establishes a total order
 * {@code SIMPLE < MEDIUM < COMPLEX} used for budget-aware downgrade: when the
 * budget snapshot reports exhaustion, the router downgrades to a cheaper tier
 * (lower level).
 *
 * <p>This is a closed three-value enum; the {@code RoutingResult.complexity}
 * field remains a {@code String} (carrying {@link #getKey()} for readable
 * audit messages) so non-SmartModelRouter implementations are not forced to
 * adopt this taxonomy.
 */
public enum Complexity {
    SIMPLE("simple", 0),
    MEDIUM("medium", 1),
    COMPLEX("complex", 2);

    private final String key;
    private final int level;

    Complexity(String key, int level) {
        this.key = key;
        this.level = level;
    }

    /**
     * @return the lowercase identifier used in {@code RoutingResult.complexity}
     *         and routing-reason audit strings
     */
    public String getKey() {
        return key;
    }

    /**
     * @return the ordinal level establishing the cheap→strong order
     *         ({@code SIMPLE=0 < MEDIUM=1 < COMPLEX=2})
     */
    public int getLevel() {
        return level;
    }
}
