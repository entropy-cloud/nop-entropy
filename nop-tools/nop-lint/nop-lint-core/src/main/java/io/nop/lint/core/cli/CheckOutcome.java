package io.nop.lint.core.cli;

import io.nop.lint.core.suppress.BaselineFile;

import java.util.List;
import java.util.Map;

/**
 * The complete outcome of one {@code nop-lint check} execution: per-file
 * findings in stable order plus the run-wide {@link RunSummary}. The exit
 * code derives from {@link #hasErrorDiagnostics()} (any error-severity
 * diagnostic → exit 1, design 03 §2.4); internal errors never reach this
 * record — they throw before it is built. The reported diagnostics always
 * describe the content on disk at the end of the run: post-fix residuals in
 * {@code --fix}, untouched-content findings in {@code --fix-dry-run}.
 *
 * <p>The fix-mode fields carry the autofix surface (roadmap item 25): the
 * run's {@link CliOptions.FixMode}, the per-file dry-run diffs, and the
 * rule-id → description map of suggestion-only fix rules whose diagnostics
 * the reporter annotates. All three are empty/NONE in a plain check run.</p>
 */
public record CheckOutcome(List<FileFindings> findings, RunSummary summary, CliOptions.FixMode fixMode,
                           List<FileDiff> diffs, Map<String, String> suggestionDescriptions,
                           List<BaselineFile.Entry> staleBaselineEntries) {

    public CheckOutcome {
        findings = List.copyOf(findings);
        diffs = List.copyOf(diffs);
        suggestionDescriptions = Map.copyOf(suggestionDescriptions);
        staleBaselineEntries = List.copyOf(staleBaselineEntries);
    }

    /**
     * The fix-flow outcome without a baseline (the pre-item-27 shape).
     */
    public CheckOutcome(List<FileFindings> findings, RunSummary summary, CliOptions.FixMode fixMode,
                        List<FileDiff> diffs, Map<String, String> suggestionDescriptions) {
        this(findings, summary, fixMode, diffs, suggestionDescriptions, List.of());
    }

    /**
     * The plain check outcome (no fix flow, no baseline) — the pre-autofix
     * shape.
     */
    public CheckOutcome(List<FileFindings> findings, RunSummary summary) {
        this(findings, summary, CliOptions.FixMode.NONE, List.of(), Map.of());
    }

    /**
     * True when any collected diagnostic carries severity {@code error}.
     */
    public boolean hasErrorDiagnostics() {
        return summary.hasErrorDiagnostics();
    }

    /**
     * True when the run leaves baseline entries the content no longer uses —
     * the {@code --baseline-check} exit-code-1 condition ("基线只减不增",
     * design 09 §5.3; only populated in check mode).
     */
    public boolean hasStaleBaselineEntries() {
        return !staleBaselineEntries.isEmpty();
    }

    /**
     * All diagnostics across all files, in render order.
     */
    public List<FileFindings> findings() {
        return List.copyOf(findings);
    }
}
