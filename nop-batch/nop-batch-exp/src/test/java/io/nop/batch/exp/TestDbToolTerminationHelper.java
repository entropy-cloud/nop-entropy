/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.exp;

import io.nop.commons.concurrent.executor.DefaultThreadPoolExecutor;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import io.nop.commons.concurrent.executor.SyncThreadPoolExecutor;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 1.7-C6-004（plan 2545 W-09；closure audit R2 MJ-1）：destroy 后有界等待的行为钉。
 * <p>
 * 缺陷态：原实现为两工具各自 private static 方法，零测试可触达；审计指出「契约钉」claim 不实。
 */
public class TestDbToolTerminationHelper {

    /** 空闲池：活跃数与队列均为 0，必须立即返回 true（不白等 30s）。 */
    @Test
    public void idleExecutorReturnsTrueImmediately() {
        IThreadPoolExecutor executor = DefaultThreadPoolExecutor.newExecutor("termination-idle", 2, 100);
        try {
            long start = System.currentTimeMillis();
            assertTrue(DbToolTerminationHelper.waitTerminated(executor, "test.terminate-timeout"),
                    "idle pool must be treated as terminated");
            long elapsed = System.currentTimeMillis() - start;
            assertTrue(elapsed < 5_000L, "idle pool wait must return promptly, elapsed=" + elapsed);
        } finally {
            executor.destroy();
        }
    }

    /** 同步池（无 worker/队列）：等待必须立即返回 true。 */
    @Test
    public void syncExecutorReturnsTrue() {
        assertTrue(DbToolTerminationHelper.waitTerminated(SyncThreadPoolExecutor.INSTANCE, "test.terminate-timeout-sync"));
    }

    /** 有在飞任务：短超时内必须返回 false（超时 WARN 分支），不得无界等待。 */
    @Test
    public void busyExecutorTimesOutWithinBound() throws Exception {
        IThreadPoolExecutor executor = DefaultThreadPoolExecutor.newExecutor("termination-busy", 1, 100);
        CountDownLatch release = new CountDownLatch(1);
        try {
            executor.submit(() -> {
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            });

            long start = System.currentTimeMillis();
            boolean terminated = DbToolTerminationHelper.waitTerminated(executor, "test.terminate-timeout-busy", 200L);
            long elapsed = System.currentTimeMillis() - start;

            assertFalse(terminated, "busy pool must not be reported terminated");
            assertTrue(elapsed >= 150L && elapsed < 10_000L,
                    "wait must be bounded by timeout, elapsed=" + elapsed);
        } finally {
            release.countDown();
            executor.destroy();
        }
    }
}
