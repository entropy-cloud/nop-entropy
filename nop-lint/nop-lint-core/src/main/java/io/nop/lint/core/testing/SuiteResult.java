package io.nop.lint.core.testing;

import java.util.List;
import java.util.Objects;

/**
 * The outcome of running one rule test suite: the suite's VFS path, the id
 * of the rule under test, and every fixture failure (empty means green).
 * Immutable value.
 */
public record SuiteResult(String suitePath, String ruleId, List<FixtureFailure> failures) {

    public SuiteResult {
        Objects.requireNonNull(suitePath, "suitePath must not be null");
        Objects.requireNonNull(ruleId, "ruleId must not be null");
        Objects.requireNonNull(failures, "failures must not be null");
        failures = List.copyOf(failures);
    }

    /**
     * True when every fixture of the suite passed.
     */
    public boolean isGreen() {
        return failures.isEmpty();
    }

    /**
     * All failures rendered one per line (the assertion message of the JUnit
     * launcher).
     */
    public String renderFailures() {
        StringBuilder sb = new StringBuilder();
        for (FixtureFailure failure : failures) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(failure.render());
        }
        return sb.toString();
    }
}
