package io.nop.refactor.core;

import java.util.List;
import java.util.Objects;

/**
 * The unified self-verification result every modify operation returns
 * (baseline §四 {@code RefactorResult} — the field set is the contract and
 * must not shrink): applied flag, per-file structured edits, the unified
 * diff, the verification face, impact statistics, and the explicit
 * non-applied enumeration.
 *
 * @param applied     false for preview (nothing written), true for apply
 * @param edits       the per-file structured edits that survived (or were
 *                    proposed, in preview)
 * @param diff        the unified diff over the touched files — the AI's
 *                    primary review surface
 * @param verification the syntax/lint/symbol self-check face
 * @param stats       impact statistics including the skip buckets
 * @param nonApplied  the edits that did not apply, each with reason and
 *                    context (never null; empty when everything applied)
 */
public record RefactorResult(boolean applied, List<FileEdit> edits, String diff,
                             Verification verification, RefactorStats stats,
                             List<NonApply> nonApplied) {

    public RefactorResult {
        Objects.requireNonNull(edits, "edits must not be null");
        Objects.requireNonNull(diff, "diff must not be null");
        Objects.requireNonNull(verification, "verification must not be null");
        Objects.requireNonNull(stats, "stats must not be null");
        Objects.requireNonNull(nonApplied,
                "nonApplied must not be null (empty when everything applied)");
        edits = List.copyOf(edits);
        nonApplied = List.copyOf(nonApplied);
    }
}
