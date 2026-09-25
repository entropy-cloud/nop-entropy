package io.nop.refactor.core;

import java.util.Objects;

/**
 * Impact statistics of one operation (baseline §四 {@code stats}:
 * "影响文件数/编辑数/跳过分桶/耗时档位").
 *
 * @param filesAffected    number of files the operation touched (or proposed
 *                         touching, in preview)
 * @param editsApplied     edits that survived (or were proposed, in preview);
 *                         rolled-back edits do not count
 * @param skipped          the non-applied edits bucketed by reason — the same
 *                         population {@code nonApplied} enumerates, in count
 *                         form
 * @param costTier         the external-bridge cost tier (baseline §四 载荷裁定:
 *                         "标注成本档位（进程内/进程外）"); the v1 codemod face
 *                         is pure in-process and always reports
 *                         {@link CostTier#IN_PROCESS}
 * @param residualRuleCount size of the configured residual-lint rule subset;
 *                         0 means "no subset configured" — machine-readable
 *                         distinction from "configured and zero residual"
 *                         (WI5 adjudication, design 01 §四 增注)
 */
public record RefactorStats(int filesAffected, int editsApplied, SkippedBuckets skipped,
                            CostTier costTier, int residualRuleCount) {

    public RefactorStats {
        Objects.requireNonNull(skipped, "skipped must not be null");
        Objects.requireNonNull(costTier, "costTier must not be null");
        if (residualRuleCount < 0)
            throw new NopRefactorException("residualRuleCount must not be negative: "
                    + residualRuleCount);
    }

    /**
     * The skip counts bucketed by {@link NonApply.Reason}: bucketing is the
     * count-form mirror of the {@code nonApplied} enumeration, so a consumer
     * can size the retry decision without walking the entries.
     */
    public record SkippedBuckets(int conflict, int outOfScope, int unresolvedTarget) {

        public int total() {
            return conflict + outOfScope + unresolvedTarget;
        }

        public static SkippedBuckets of(Iterable<NonApply> nonApplied) {
            int conflict = 0;
            int outOfScope = 0;
            int unresolved = 0;
            for (NonApply nonApply : nonApplied) {
                switch (nonApply.reason()) {
                    case CONFLICT -> conflict++;
                    case OUT_OF_SCOPE -> outOfScope++;
                    case UNRESOLVED_TARGET -> unresolved++;
                }
            }
            return new SkippedBuckets(conflict, outOfScope, unresolved);
        }
    }

    public enum CostTier {
        /** computed entirely inside this JVM (the v1 default path). */
        IN_PROCESS,
        /** part of the computation ran in an external pooled process. */
        OUT_OF_PROCESS
    }
}
