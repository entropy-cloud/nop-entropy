/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.executor;

import io.nop.commons.concurrent.QueueOverflowPolicy;
import io.nop.commons.concurrent.impl.OverflowBlockingQueue;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestSequentialTaskExecutor {

    @Test
    public void testTotalExecutionTimeUnit() {
        SequentialTaskExecutor executor = new SequentialTaskExecutor(Runnable::run,
                new OverflowBlockingQueue<>(100, QueueOverflowPolicy.ERROR), -1);

        executor.execute(() -> {
            try {
                Thread.sleep(120);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // totalExecutionTime累加的是纳秒，getter必须按纳秒返回。
        // 任务耗时约120ms，合理范围是 [50ms, 10s) 对应的纳秒数
        long total = executor.getTotalExecutionTime();
        assertTrue(total >= TimeUnit.MILLISECONDS.toNanos(50), "total=" + total);
        assertTrue(total < TimeUnit.MILLISECONDS.toNanos(10_000), "total=" + total);
    }
}
