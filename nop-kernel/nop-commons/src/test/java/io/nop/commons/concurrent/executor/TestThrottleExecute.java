/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.executor;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static io.nop.commons.CommonConfigs.CFG_CONCURRENT_THROTTLE_ACQUIRE_TIMEOUT;
import static io.nop.commons.CommonErrors.ERR_CONCURRENT_THROTTLE_ACQUIRE_TIMEOUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2548 R4-14（C7-010）throttleExecute 有界等待载体钉。
 */
public class TestThrottleExecute {

    @AfterEach
    public void restoreConfig() {
        AppConfig.getConfigProvider().updateConfigValue(CFG_CONCURRENT_THROTTLE_ACQUIRE_TIMEOUT, null);
    }

    private static final Executor DIRECT = Runnable::run;

    /** pin7010a：许可耗尽 + 短超时 → future 异常终态且 cause 类型化（ERR_CONCURRENT_THROTTLE_ACQUIRE_TIMEOUT）。 */
    @Test
    public void pin7010a_timeoutTypedException() throws Exception {
        AppConfig.getConfigProvider().updateConfigValue(CFG_CONCURRENT_THROTTLE_ACQUIRE_TIMEOUT, 100L);
        Semaphore semaphore = new Semaphore(0); // 永不释放
        CompletableFuture<Void> future = ExecutorHelper.throttleExecute(DIRECT, semaphore, () -> {
        });

        ExecutionException ex = assertThrows(ExecutionException.class, () -> future.get(2, TimeUnit.SECONDS));
        assertTrue(ex.getCause() instanceof NopException, "cause 应类型化，实际 " + ex.getCause());
        assertEquals(ERR_CONCURRENT_THROTTLE_ACQUIRE_TIMEOUT.getErrorCode(),
                ((NopException) ex.getCause()).getErrorCode());
    }

    /** pin7010b：中断 → future 异常终态 + 中断标志恢复（可中断分支，timeout>0）。 */
    @Test
    public void pin7010b_interruptRestoresFlag() throws Exception {
        AppConfig.getConfigProvider().updateConfigValue(CFG_CONCURRENT_THROTTLE_ACQUIRE_TIMEOUT, 10_000L);
        Semaphore semaphore = new Semaphore(0); // 无人释放 → 阻塞在 tryAcquire
        Thread.currentThread().interrupt(); // 预置中断 → tryAcquire 立即抛 InterruptedException
        try {
            CompletableFuture<Void> future = ExecutorHelper.throttleExecute(DIRECT, semaphore, () -> {
            });
            assertTrue(future.isCompletedExceptionally(), "中断应立即异常终态");
            assertTrue(Thread.currentThread().isInterrupted(), "中断标志应恢复");
        } finally {
            Thread.interrupted(); // 清除标志
        }
    }

    /** pin7010c：正常获许可路径不受影响。 */
    @Test
    public void pin7010c_normalAcquireWorks() throws Exception {
        Semaphore semaphore = new Semaphore(1);
        CompletableFuture<Void> future = ExecutorHelper.throttleExecute(DIRECT, semaphore, () -> {
        });
        future.get(2, TimeUnit.SECONDS);
        assertEquals(1, semaphore.availablePermits(), "任务完成后许可应释放");
        assertFalse(future.isCompletedExceptionally());
    }
}
