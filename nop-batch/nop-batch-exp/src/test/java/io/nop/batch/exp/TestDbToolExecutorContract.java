/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.exp;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.concurrent.executor.DefaultThreadPoolExecutor;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 工单 2.1-C4-003 缺陷面调用方回归（nop-batch-exp）：ExportDbTool/ImportDbTool 以修复后参数
 * （有界队列 10000，与 globalWorker TaskQueue 容量一致）构造成功且池可用，语义与池工厂校验契约
 * （设计文档 pool-facility-hardening.md §3.3 兼容矩阵 #1/#2）一致；工厂调用仅在 threadCount>1
 * 分支发生，threadCount≥2 恒过校验。队列有界性语义由 nop-commons 设施测试钉死。
 */
public class TestDbToolExecutorContract {

    @Test
    public void testBoundedQueueExecutorConstructsAfterFix() throws Exception {
        for (int threadCount = 2; threadCount <= 4; threadCount++) {
            DefaultThreadPoolExecutor exportExecutor = DefaultThreadPoolExecutor.newExecutor("export-db",
                    threadCount, 10000);
            try {
                assertEquals(threadCount, exportExecutor.stats().getCorePoolSize());
                assertEquals(threadCount, exportExecutor.stats().getMaxPoolSize());
                assertEquals(42, exportExecutor.submit(() -> 42).get(5, TimeUnit.SECONDS),
                        "export-db pool must execute tasks after fix");
            } finally {
                exportExecutor.destroy();
            }

            DefaultThreadPoolExecutor importExecutor = DefaultThreadPoolExecutor.newExecutor("import-db",
                    threadCount, 10000);
            try {
                assertEquals(threadCount, importExecutor.stats().getCorePoolSize());
                assertEquals(threadCount, importExecutor.stats().getMaxPoolSize());
                CompletableFuture<Integer> done = importExecutor.submit(() -> 42);
                assertEquals(42, done.get(5, TimeUnit.SECONDS), "import-db pool must execute tasks after fix");
            } finally {
                importExecutor.destroy();
            }
        }
    }

    @Test
    public void testNegativeLegacyParamsNowRejected() {
        // 旧的负值传参形态按工厂校验契约封死（不再静默变 0-core 无界 cached 池），违例类型化
        NopException ex = assertThrows(NopException.class,
                () -> DefaultThreadPoolExecutor.newExecutor("export-db", -1, -1));
        assertEquals("nop.err.commons.concurrent.invalid-thread-pool-param", ex.getErrorCode());
    }
}
