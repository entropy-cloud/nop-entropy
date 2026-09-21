package io.nop.lint.core.cli;

import java.util.List;

/**
 * The complete outcome of one {@code nop-lint check} execution: per-file
 * findings in stable order plus the run-wide {@link RunSummary}. The exit
 * code derives from {@link #hasErrorDiagnostics()} (any error-severity
 * diagnostic → exit 1, design 03 §2.4); internal errors never reach this
 * record — they throw before it is built.
 */
public record CheckOutcome(List<FileFindings> findings, RunSummary summary) {

    public CheckOutcome {
        findings = List.copyOf(findings);
    }

    /**
     * True when any collected diagnostic carries severity {@code error}.
     */
    public boolean hasErrorDiagnostics() {
        return summary.hasErrorDiagnostics();
    }

    /**
     * All diagnostics across all files, in render order.
     */
    public List<FileFindings> findings() {
        return List.copyOf(findings);
    }
}
