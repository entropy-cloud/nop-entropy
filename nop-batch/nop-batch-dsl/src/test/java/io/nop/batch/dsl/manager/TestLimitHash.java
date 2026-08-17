package io.nop.batch.dsl.manager;

import io.nop.batch.dsl.model.BatchTaskModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 缺陷 12b 回归: limitHash 对 Integer.MIN_VALUE 取 abs 仍为负，返回负 partition 索引。
 * 修复: 改用 Math.floorMod。见 ai-dev/analysis/2026-08/2026-08-16-nop-batch-design-and-concurrency-review.md
 */
public class TestLimitHash {
    @Test
    public void defect12b_limitHashMustBeNonNegative() {
        ModelBasedBatchTaskBuilderFactory factory = new ModelBasedBatchTaskBuilderFactory(
                new BatchTaskModel(), null, null, null, null, null, null, null);

        int hash = factory.limitHash(Integer.MIN_VALUE);
        assertTrue(hash >= 0, "partition index must be in [0, Short.MAX_VALUE), actual: " + hash);
        // 常规值不受影响
        assertTrue(factory.limitHash(5) >= 0);
        assertTrue(factory.limitHash(-7) >= 0);
    }
}
