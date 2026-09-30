/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.shard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 369 Phase 3: the O(1) closed-form rewrite of
 * {@link KeyGroupAssignment#assignKeyGroupToSubtask} must be BIT-EXACT with
 * the former O(P) descending scan over range starts
 * {@code start(i) = i*base + min(i, rem)} (the inverse of
 * {@link KeyGroupAssignment#computeKeyGroupRangeForSubtaskIndex}). The legacy
 * scan is rewritten HERE (in the test) as the reference implementation; the
 * production class keeps only the closed form.
 *
 * <p>The grid covers sampled {@code maxParallelism} values in [1, 40], the
 * powers-of-two/growth anchors up to 127, and the default 128; for each, EVERY
 * parallelism in [1, maxParallelism] and EVERY key group in
 * [0, maxParallelism) is compared — the full (maxParallelism x parallelism x
 * keyGroup) grid, not a sample.
 *
 * <p>Deliberate divergence: the Flink formula {@code g*P/M} is NOT equivalent
 * at partition boundaries (M=15, P=10, g=3: nop=1, Flink=2) — pinned by
 * {@link #notTheFlinkFormula} so nobody "simplifies" it back.
 */
class TestKeyGroupAssignmentClosedForm {

    /** Reference implementation: the legacy O(P) descending scan (pre-plan-369). */
    private static int assignKeyGroupToSubtaskLegacyScan(int keyGroupId, int maxParallelism, int parallelism) {
        int base = maxParallelism / parallelism;
        int rem = maxParallelism % parallelism;
        for (int i = parallelism - 1; i >= 0; i--) {
            int start = i * base + Math.min(i, rem);
            if (keyGroupId >= start) {
                return i;
            }
        }
        return 0;
    }

    /** Sampled max-parallelism values (1..40 stride + anchors + default 128). */
    private static final int[] MAX_PARALLELISM_GRID = {
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
            17, 18, 19, 20, 22, 24, 26, 28, 30, 32, 33, 34, 36, 38, 40,
            48, 63, 64, 65, 96, 100, 127, 128
    };

    @Test
    void fullGridClosedFormEqualsLegacyScanAndRangeOwner() {
        for (int maxParallelism : MAX_PARALLELISM_GRID) {
            for (int parallelism = 1; parallelism <= maxParallelism; parallelism++) {
                for (int keyGroup = 0; keyGroup < maxParallelism; keyGroup++) {
                    int closedForm = KeyGroupAssignment.assignKeyGroupToSubtask(keyGroup, maxParallelism, parallelism);
                    int legacy = assignKeyGroupToSubtaskLegacyScan(keyGroup, maxParallelism, parallelism);
                    assertEquals(legacy, closedForm,
                            "closed form diverges from legacy scan at maxParallelism=" + maxParallelism
                                    + ", parallelism=" + parallelism + ", keyGroup=" + keyGroup);

                    // The owner's declared range must contain the key group
                    // (independent property, not just scan-vs-formula identity).
                    KeyGroupRange ownerRange = KeyGroupAssignment.computeKeyGroupRangeForSubtaskIndex(
                            maxParallelism, parallelism, closedForm);
                    assertTrue(ownerRange.contains(keyGroup),
                            "owner " + closedForm + " range " + ownerRange + " does not contain group " + keyGroup
                                    + " (maxParallelism=" + maxParallelism + ", parallelism=" + parallelism + ")");
                }
            }
        }
    }

    @Test
    void spotChecks() {
        assertEquals(6, KeyGroupAssignment.assignKeyGroupToSubtask(100, 128, 8));
        assertEquals(2, KeyGroupAssignment.assignKeyGroupToSubtask(7, 10, 3));
        assertEquals(3, KeyGroupAssignment.assignKeyGroupToSubtask(6, 15, 10));
        assertEquals(1, KeyGroupAssignment.assignKeyGroupToSubtask(3, 15, 10));
        // Boundary groups: first and last group map to the outer subtasks.
        assertEquals(0, KeyGroupAssignment.assignKeyGroupToSubtask(0, 15, 10));
        assertEquals(9, KeyGroupAssignment.assignKeyGroupToSubtask(14, 15, 10));
        // Single subtask owns everything.
        assertEquals(0, KeyGroupAssignment.assignKeyGroupToSubtask(127, 128, 1));
    }

    @Test
    void notTheFlinkFormula() {
        // The closed form is deliberately NOT Flink's g*P/M: at M=15, P=10,
        // g=3 Flink yields 2 while the nop partition (first rem=5 subtasks own
        // 2 groups each) yields 1. Swapping formulas would corrupt rescale
        // ownership for every non-even split.
        int flinkFormula = 3 * 10 / 15;
        assertEquals(2, flinkFormula, "sanity: Flink formula gives 2 at (M=15,P=10,g=3)");
        assertEquals(1, KeyGroupAssignment.assignKeyGroupToSubtask(3, 15, 10),
                "nop ownership must stay with the legacy partition, not the Flink formula");
    }
}
