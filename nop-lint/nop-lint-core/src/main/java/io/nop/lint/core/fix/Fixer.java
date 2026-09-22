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
     * number of candidates dropped as conflicts.
     */
    public record MergeResult(List<Fix> applied, int skippedConflicts) {
    }

    /**
     * Greedy non-overlapping selection over the priority-ordered candidates.
     * A candidate overlapping any kept fix is a conflict; identical ranges
     * conflict likewise (the first candidate in priority order wins).
     */
    public static MergeResult merge(List<Fix> candidates) {
        List<Fix> kept = new ArrayList<>();
        int conflicts = 0;
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
                conflicts++;
            } else {
                kept.add(candidate);
            }
        }
        kept.sort(Comparator.comparingInt((Fix f) -> f.range().startByte())
                .thenComparingInt(f -> f.range().endByte()));
        return new MergeResult(List.copyOf(kept), conflicts);
    }
}
