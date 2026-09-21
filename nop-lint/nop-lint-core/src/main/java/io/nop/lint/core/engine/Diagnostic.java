package io.nop.lint.core.engine;

import io.nop.lint.core.node.SourceRange;

import java.util.Objects;

/**
 * One lint finding (design 03 §2.3): the rule that produced it, the rule's
 * configured severity and message, and the byte range of the offending node.
 * Immutable value.
 */
public record Diagnostic(String ruleId, String severity, String message, SourceRange range) {

    public Diagnostic {
        Objects.requireNonNull(ruleId, "ruleId must not be null");
        Objects.requireNonNull(severity, "severity must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(range, "range must not be null");
    }
}
