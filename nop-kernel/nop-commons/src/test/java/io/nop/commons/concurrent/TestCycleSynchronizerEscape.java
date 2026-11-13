/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2548 R4-14（C7-011 Decision）逃生门行为观测载体钉。
 */
public class TestCycleSynchronizerEscape {

    /** pin7011：推进者死亡（processing 卡死）→ beginCycle 抛类型化异常 → restart() 解除 → 新周期可正常开始。 */
    @Test
    public void pin7011_restartEscapeDoorWorks() {
        CycleSynchronizer sync = new CycleSynchronizer();
        assertTrue(sync.beginCycle());
        assertTrue(sync.isProcessing());

        // 推进者死亡：processing 卡在 true（本类不自动超时清理）
        assertThrows(Exception.class, sync::beginCycle, "processing 期间 beginCycle 应抛 ERR_CONCURRENT_CYCLE_ALREADY_BEGIN");
        assertTrue(sync.isProcessing(), "无自动清理：processing 保持 true");

        // 外部逃生门：restart() 解除
        sync.restart();
        assertFalse(sync.isProcessing(), "restart 后 processing 复位");

        // 解除后新周期可正常开始（2547 generation 语义不回退：pin0110 系列守护）
        assertTrue(sync.beginCycle());
        sync.finish();
    }
}
