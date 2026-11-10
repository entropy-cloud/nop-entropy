/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.migration;

import io.nop.orm.migration.TargetShardMapping.PhysicalTable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.5-C3-011（plan 2554 裁定 I）载体：FULL 复制 taskKey 追加 querySpace 维度——
 * 多 querySpace 同名物理表的任务身份（batch 断点/管理面键）不撞。
 */
public class TestFullCopyTaskKeyQs {

    @Test
    public void testSameTableNameDifferentQuerySpaceDistinctTaskKeys() {
        PhysicalTable qsA = new PhysicalTable("qs-a", "MIG_ORDER");
        PhysicalTable qsB = new PhysicalTable("qs-b", "MIG_ORDER");

        String keyA = FullCopyBatchJob.taskKey("job1", qsA);
        String keyB = FullCopyBatchJob.taskKey("job1", qsB);
        assertNotEquals(keyA, keyB,
                "same-name physical tables in different query spaces must not collide on taskKey");
        assertEquals("job1:qs-a:MIG_ORDER", keyA);
        assertEquals("job1:qs-b:MIG_ORDER", keyB);
    }

    @Test
    public void testTaskKeyShapeKeepsJobIdAndTableDimensions() {
        // 缺省 querySpace（空串）形态：jobId 与表维度保留，qs 段显式留空（不回退旧形态）
        assertEquals("job2::MIG_ORDER", FullCopyBatchJob.taskKey("job2", new PhysicalTable("", "MIG_ORDER")));
        String legacyShape = "job2:MIG_ORDER";
        assertNotEquals(legacyShape, FullCopyBatchJob.taskKey("job2", new PhysicalTable("", "MIG_ORDER")),
                "taskKey must keep the explicit qs segment (3-part shape)");
        assertTrue(FullCopyBatchJob.taskKey("job3", new PhysicalTable("qs", "T")).endsWith(":T"));
    }
}
