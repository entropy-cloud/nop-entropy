package io.nop.lint.core.testing;

import java.util.Objects;

/**
 * One actionable fixture failure: which suite, which fixture file, which
 * rule (when known), and a problem description that carries the expected vs
 * actual contrast. Immutable value.
 */
public record FixtureFailure(String suite, String fixturePath, String ruleId, String problem) {

    public FixtureFailure {
        Objects.requireNonNull(suite, "suite must not be null");
        Objects.requireNonNull(fixturePath, "fixturePath must not be null");
        Objects.requireNonNull(problem, "problem must not be null");
    }

    /**
     * One-line rendering used in assertion messages.
     */
    public String render() {
        StringBuilder sb = new StringBuilder();
        sb.append(suite).append(": ").append(fixturePath);
        if (ruleId != null) {
            sb.append(" [").append(ruleId).append(']');
        }
        return sb.append(" - ").append(problem).toString();
    }
}
