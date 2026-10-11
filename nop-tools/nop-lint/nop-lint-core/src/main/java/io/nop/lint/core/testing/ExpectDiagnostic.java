package io.nop.lint.core.testing;

import java.util.Objects;

/**
 * One expected diagnostic of an invalid fixture (design 03 §4.2, v1 field
 * set): a 1-based {@code line}/{@code endLine} source range, the id of the
 * rule that must fire, and a fragment the diagnostic message must contain.
 * Immutable value.
 */
public record ExpectDiagnostic(int line, int endLine, String ruleId, String messageContains) {

    public ExpectDiagnostic {
        Objects.requireNonNull(ruleId, "ruleId must not be null");
        Objects.requireNonNull(messageContains, "messageContains must not be null");
    }

    @Override
    public String toString() {
        return "{line=" + line + ", endLine=" + endLine + ", ruleId=" + ruleId
                + ", messageContains=" + messageContains + "}";
    }
}
