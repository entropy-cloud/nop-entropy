/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.executor;

import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2547 R4-13（C4-002）destroy 语义载体钉（端到端，非 mock）。
 * <p>
 * 三腿：① await>0 时有界等待在飞任务完成；② await=0 时保持 shutdown() 现语义（不等）；
 * ③ init() 前 destroy 抛类型化异常而非 NPE。
 */
public class TestThreadPoolDestroyAwaitProbe {

    /** pin0402a：waitForTasksToCompleteOnShutdown=true + awaitTerminationSeconds=2 → destroy 等任务完成。 */
    @Test
    public void pin0402a_destroyWaitsForInFlightTasks() throws Exception {
        ThreadPoolConfig config = new ThreadPoolConfig();
        config.setName("probe-await");
        config.setCorePoolSize(2);
        config.setMaxPoolSize(2);
        config.setWaitForTasksToCompleteOnShutdown(true);
        config.setAwaitTerminationSeconds(2);

        DefaultThreadPoolExecutor executor = new DefaultThreadPoolExecutor();
        executor.setConfig(config);
        executor.init();

        AtomicBoolean taskCompleted = new AtomicBoolean();
        CountDownLatch started = new CountDownLatch(1);
        executor.submit(() -> {
            started.countDown();
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            taskCompleted.set(true);
            return null;
        });

        assertTrue(started.await(5, TimeUnit.SECONDS), "任务必须已开始执行");
        executor.destroy();

        assertTrue(taskCompleted.get(),
                "destroy 返回时在飞任务必须已完成（有界等待生效）");
    }

    /** pin0402b：awaitTerminationSeconds=0 → 保持 shutdown() 现语义（不等待/不中断）。 */
    @Test
    public void pin0402b_zeroAwaitKeepsShutdownSemantics() throws Exception {
        ThreadPoolConfig config = new ThreadPoolConfig();
        config.setName("probe-noawait");
        config.setCorePoolSize(2);
        config.setMaxPoolSize(2);
        config.setWaitForTasksToCompleteOnShutdown(true);
        config.setAwaitTerminationSeconds(0);

        DefaultThreadPoolExecutor executor = new DefaultThreadPoolExecutor();
        executor.setConfig(config);
        executor.init();

        AtomicBoolean interrupted = new AtomicBoolean();
        CountDownLatch started = new CountDownLatch(1);
        executor.submit(() -> {
            started.countDown();
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
            }
            return null;
        });

        assertTrue(started.await(5, TimeUnit.SECONDS));
        long t0 = System.currentTimeMillis();
        executor.destroy();
        long elapsed = System.currentTimeMillis() - t0;

        assertTrue(elapsed < 250, "await=0 时 destroy 不得等待，实际耗时=" + elapsed + "ms");
        assertFalse(interrupted.get(), "await=0 时不得 shutdownNow 中断在飞任务（保持现语义）");
    }

    /** pin0402c：init 前 destroy 抛类型化异常而非裸 NPE。 */
    @Test
    public void pin0402c_destroyBeforeInitFailsTyped() {
        ThreadPoolConfig config = new ThreadPoolConfig();
        config.setName("probe-broken");
        DefaultThreadPoolExecutor executor = new DefaultThreadPoolExecutor();
        executor.setConfig(config);

        NopException e = assertThrows(NopException.class, executor::destroy);
        assertEquals("nop.err.commons.concurrent.thread-pool-not-initialized", e.getErrorCode());
    }
}
