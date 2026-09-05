/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.tdengine.driver;

import io.nop.orm.driver.IEntityPersistDriver;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 回归覆盖审查报告 TD-01：TdEntityPersistDriver.batchExecuteAsync必须返回非null的CompletionStage。
 * 修复前所有路径return null，与IEntityPersistDriver契约偏离（当前调用方FutureHelper.collectWaiting
 * 判空容忍不崩溃，但新增调用方按非null假设编码即NPE）。
 */
public class TestTdEntityPersistDriverContract {

    @Test
    public void testBatchExecuteAsyncReturnsNonNullFutureWhenNoActions() {
        IEntityPersistDriver driver = new TdEntityPersistDriver();

        // 无任何动作时不触碰实体模型/环境，直接返回已完成future
        CompletionStage<Void> ret = driver.batchExecuteAsync(true, "qs", null, null, null, null);
        assertNotNull(ret, "无save/update/delete动作时应返回已完成future而非null");

        ret = driver.batchExecuteAsync(false, "qs", null, null, null, null);
        assertNotNull(ret, "无delete动作时应返回已完成future而非null");
    }
}
