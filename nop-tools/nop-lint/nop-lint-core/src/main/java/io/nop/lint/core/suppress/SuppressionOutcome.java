package io.nop.lint.core.suppress;

import io.nop.lint.core.engine.Diagnostic;

import java.util.List;
import java.util.Objects;

/**
 * The outcome of the suppression pass over one lint run (design 03 §1.1
 * pipeline tail): the emitted diagnostics — the surviving candidates in
 * their original order followed by the engine-generated meta-diagnostics —
 * the suppressed candidates (the monotonic judgment's observable
 * counterpart: what was removed and why it stays out of the result), and
 * the meta-diagnostics alone.
 */
public record SuppressionOutcome(List<Diagnostic> diagnostics, List<Diagnostic> suppressed,
                                List<Diagnostic> metaDiagnostics) {

    public SuppressionOutcome {
        Objects.requireNonNull(diagnostics, "diagnostics must not be null");
        Objects.requireNonNull(suppressed, "suppressed must not be null");
        Objects.requireNonNull(metaDiagnostics, "metaDiagnostics must not be null");
        diagnostics = List.copyOf(diagnostics);
        suppressed = List.copyOf(suppressed);
        metaDiagnostics = List.copyOf(metaDiagnostics);
    }
}
