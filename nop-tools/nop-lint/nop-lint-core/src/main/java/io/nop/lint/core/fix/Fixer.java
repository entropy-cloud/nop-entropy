package io.nop.lint.core.fix;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The multi-fix merge (roadmap item 25, design 04 §7): candidates arrive in
 * priority order — ruleset declaration order, then match generation order —
 * and a greedy pass keeps every fix whose byte range does not overlap an
 * already-kept one; the survivors are then ordered by range start for
 * application. Overlapping candidates are conflicts: the earlier-priority
 * candidate wins and the rest are counted
 * ({@code skipped-conflict}, never dropped silently).
 *
 * <p>The "ranges are disjoint" assumption the naive reading of design 03 §3
 * makes does not hold — nested same-kind matches can overlap or coincide —
 * so this priority order is the single conflict authority (plan
 * 2026-09-22-2225-1 Decision).</p>
 */
public final class Fixer {

    private Fixer() {
    }

    /**
     * The merge outcome: the applicable fixes in application order plus the
     * candidates dropped as conflicts. Since nop-refactor WI5 the dropped
     * candidates ride along as the {@code skipped} list (additive, plan
     * 05-wi5-refactor-result-verification-payload adjudication: the
     * RefactorResult nonApplied(CONFLICT) context needs the edit bodies, and
     * this list is the single source — recomputing it from input-minus-applied
     * would duplicate the merge authority). {@link #skippedConflicts()} keeps
     * the historical count accessor working unchanged.
     */
    public record MergeResult(List<Fix> applied, List<Fix> skipped) {

        public MergeResult {
            skipped = skipped == null ? List.of() : List.copyOf(skipped);
        }

        public int skippedConflicts() {
            return skipped.size();
        }
    }

        /**
     * Greedy non-overlapping selection in declaration order: earlier fixes
     * win, later overlapping ones are skipped and counted. The overlap scan
     * is quadratic in the kept count — deliberate: the priority contract is
     * declaration order (not earliest-deadline interval scheduling), so a
     * sorted sweep would change semantics, and real files present dozens of
     * candidates, not thousands (plan 08 scale note).
     */
    public static MergeResult merge(List<Fix> candidates) {
        List<Fix> kept = new ArrayList<>();
        List<Fix> skipped = new ArrayList<>();
        for (Fix candidate : candidates) {
            boolean overlaps = false;
            for (Fix chosen : kept) {
                if (candidate.range().startByte() < chosen.range().endByte()
                        && chosen.range().startByte() < candidate.range().endByte()) {
                    overlaps = true;
                    break;
                }
            }
            if (overlaps) {
                skipped.add(candidate);
            } else {
                kept.add(candidate);
            }
        }
        kept.sort(Comparator.comparingInt((Fix f) -> f.range().startByte())
                .thenComparingInt(f -> f.range().endByte()));
        return new MergeResult(List.copyOf(kept), List.copyOf(skipped));
    }
}
