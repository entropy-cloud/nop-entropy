/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.migration;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.5-C3-012（plan 2554 裁定 J）载体：位点三方对齐 fail-fast——seed 源表集 ⊄ CDC 订阅清单
 * 源表集即类型化失败（ERR_MIG_SEED_TABLE_MISALIGNED），全对齐放行。
 */
public class TestCdcSeedThreeWayAlignment {

    @Test
    public void testMisalignedSeedFailsTyped() {
        // seed 覆盖 A、B 两表，订阅清单只含 A → B 的增量事件将被静默丢弃 → fail-fast
        io.nop.api.core.exceptions.NopException e = assertThrows(
                io.nop.api.core.exceptions.NopException.class,
                () -> CdcOffsetSeedBuilder.checkThreeWayAlignment("seed-align-1",
                        List.of("MIG_A", "MIG_B"), List.of("MIG_A")));
        assertEquals(MigrationErrors.ERR_MIG_SEED_TABLE_MISALIGNED.getErrorCode(), e.getErrorCode());
        assertEquals("seed-align-1", e.getParams().get(MigrationErrors.ARG_JOB_ID));
        assertEquals("MIG_A,MIG_B", e.getParams().get(MigrationErrors.ARG_SEED_TABLES));
        assertEquals("MIG_A", e.getParams().get(MigrationErrors.ARG_TABLE_INCLUDE_LIST));
    }

    @Test
    public void testAlignedSeedPasses() {
        // 全对齐（seed 表集 ⊆ 订阅清单）放行
        CdcOffsetSeedBuilder.checkThreeWayAlignment("seed-align-2",
                List.of("MIG_A", "MIG_B"), List.of("MIG_A", "MIG_B", "MIG_C"));
        CdcOffsetSeedBuilder.checkThreeWayAlignment("seed-align-3",
                List.of(), List.of("MIG_A"));
    }

    @Test
    public void testChainStartConsumesAlignmentCheck() {
        // 链启动入口（startFallbackChains）接线结构钉：manager 源码在构造 connector 配置前消费
        // 单点判定函数（裁定 J：seed build / chain start 双面收敛到同点）
        java.nio.file.Path path = java.nio.file.Path.of(
                "src/main/java/io/nop/orm/migration/IncrementalJobManager.java");
        try {
            String source = java.nio.file.Files.readString(path);
            assertTrue(source.contains("CdcOffsetSeedBuilder.checkThreeWayAlignment"),
                    "chain start must consume the single-point three-way alignment check");
            int callAt = source.indexOf("CdcOffsetSeedBuilder.checkThreeWayAlignment");
            int loopAt = source.indexOf("for (int i = 0; i < targets.size(); i++)");
            assertTrue(callAt >= 0 && loopAt > callAt,
                    "alignment check must run before any chain starts");
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
