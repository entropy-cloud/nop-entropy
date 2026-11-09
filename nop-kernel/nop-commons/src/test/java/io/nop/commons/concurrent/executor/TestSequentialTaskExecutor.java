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

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
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

    /**
     * plan 2547 R4-13（C3-008）pin0308：队列满时 execute 必须**显式拒绝**（抛 REE），
     * 而非静默丢弃任务。缺陷态：`queue.offer(command)` 返回值被忽略。
     * <p>
     * 用「拒绝一切」的队列模拟满态（OverflowBlockingQueue 在 ERROR 策略下满时返回 false）。
     */
    @Test
    public void pin0308_fullQueueRejectsLoudly() {
        // 容量 1 + ERROR 策略：第二个 offer 返回 false（不阻塞）
        SequentialTaskExecutor executor = new SequentialTaskExecutor(
                cmd -> { /* 不执行，保持队列不被消费以构造"满"态 */ },
                new OverflowBlockingQueue<>(1, QueueOverflowPolicy.ERROR), -1);

        executor.execute(() -> { });   // 占满
        // 队列满时必须显式拒绝（抛 REE），不得静默丢弃任务
        assertThrows(RejectedExecutionException.class, () -> executor.execute(() -> { }),
                "队列满时 execute 必须抛 RejectedExecutionException（不得静默丢弃）");
    }
}
