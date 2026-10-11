package io.nop.lint.core.suppress;

import io.nop.lint.core.engine.Diagnostic;
import io.nop.lint.core.node.SourceRange;

import java.util.List;
import java.util.Objects;

/**
 * The comment scan of one tree (design 09 §2): the suppression spans the
 * directives opened, the pairing meta-diagnostics (unpaired disable/enable),
 * and the byte ranges of the disable-type directives — the identities the
 * {@code unused-disable-directive} check reports against, including
 * directives that opened no span (a redundant re-disable).
 */
public record SuppressionScan(List<SuppressionSpan> spans, List<Diagnostic> metaDiagnostics,
                              List<SourceRange> disableDirectiveRanges) {

    public SuppressionScan {
        Objects.requireNonNull(spans, "spans must not be null");
        Objects.requireNonNull(metaDiagnostics, "metaDiagnostics must not be null");
        Objects.requireNonNull(disableDirectiveRanges, "disableDirectiveRanges must not be null");
        spans = List.copyOf(spans);
        metaDiagnostics = List.copyOf(metaDiagnostics);
        disableDirectiveRanges = List.copyOf(disableDirectiveRanges);
    }
}
