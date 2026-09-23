package io.nop.lint.core.engine;

import io.nop.lint.core.fix.Fix;
import io.nop.lint.core.node.SourceRange;

import java.util.Objects;

/**
 * One lint finding (design 03 §2.3): the rule that produced it, the rule's
 * configured severity and message, the byte range of the offending node,
 * and — for rules that declare an autofix template (roadmap item 25) — the
 * concrete rewrite rendered at match time. The fix rides on the diagnostic
 * so the suppression judgment removes both together (a suppressed diagnostic
 * produces no fix, design 03 §3); a diagnostic without a fix carries null.
 * Immutable value.
 */
public record Diagnostic(String ruleId, String severity, String message, SourceRange range,
                         Fix fix) {

    public Diagnostic {
        Objects.requireNonNull(ruleId, "ruleId must not be null");
        Objects.requireNonNull(severity, "severity must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(range, "range must not be null");
    }

    public Diagnostic(String ruleId, String severity, String message, SourceRange range) {
        this(ruleId, severity, message, range, null);
    }
}
