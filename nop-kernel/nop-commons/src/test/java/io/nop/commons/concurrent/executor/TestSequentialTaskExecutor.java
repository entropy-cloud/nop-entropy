/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.executor;

import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.concurrent.QueueOverflowPolicy;
import io.nop.commons.concurrent.impl.OverflowBlockingQueue;
import io.nop.commons.test.time.ManualClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 时间确定化（plan 2513-WI7）：SequentialTaskExecutor 的耗时统计读 {@code CoreMetrics.nanoTime()}，
 * 测试注册 ManualClock 虚拟时钟；任务在推进线程上执行并显式推进虚拟时间，耗时累计为确定值。
 */
public class TestSequentialTaskExecutor {

    private ManualClock clock;

    @BeforeEach
    public void registerManualClock() {
        clock = new ManualClock(1_000_000L);
        CoreMetrics.registerClock(clock);
    }

    @AfterEach
    public void restoreDefaultClock() {
        CoreMetrics.registerClock(CoreMetrics.defaultClock());
    }

    @Test
    public void testTotalExecutionTimeUnit() {
        SequentialTaskExecutor executor = new SequentialTaskExecutor(Runnable::run,
                new OverflowBlockingQueue<>(100, QueueOverflowPolicy.ERROR), -1);

        // execute 在当前线程内联执行任务；任务把虚拟时钟推进 120ms，被测的耗时累计即为确定的 120ms
        executor.execute(() -> clock.advance(120));

        // totalExecutionTime累加的是纳秒，getter必须按纳秒返回：120ms 对应 120_000_000 纳秒
        long total = executor.getTotalExecutionTime();
        assertEquals(TimeUnit.MILLISECONDS.toNanos(120), total, "total=" + total);
    }
}
