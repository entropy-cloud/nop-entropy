package io.nop.task.impl;

import io.nop.commons.concurrent.ratelimit.IRateLimiter;
import io.nop.commons.concurrent.semaphore.ISemaphore;
import io.nop.core.initialize.CoreInitialization;
import io.nop.task.ITask;
import io.nop.task.ITaskRuntime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * check2 P3-1/P3-2 回归测试：全局限流器/信号量统计。
 *
 * <ul>
 *   <li>P3-1：resetGlobalStats 必须同时重置限流器统计（修复前只重置信号量）。</li>
 *   <li>P3-2：同 key 不同参数的全局配置命中缓存实例时不抛错、返回同一实例（参数固化行为以 warn 暴露）。</li>
 * </ul>
 */
public class TestTaskFlowManagerGlobalStats {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void resetGlobalStats_resetsRateLimitersAndSemaphores() {
        TaskFlowManagerImpl taskFlowManager = new TaskFlowManagerImpl();
        ITaskRuntime taskRt = newTaskRuntime(taskFlowManager);

        IRateLimiter limiter = taskFlowManager.getRateLimiter(taskRt, "stats-key", 100.0, true);
        assertTrue(limiter.tryAcquire(), "rate limiter acquire must succeed with spare capacity");

        ISemaphore semaphore = taskFlowManager.getSemaphore(taskRt, "stats-key", 1, true);
        assertTrue(semaphore.tryAcquire(1, 0), "semaphore acquire must succeed");

        String rateKey = taskRt.getTaskName() + ":stats-key";
        assertTrue(taskFlowManager.getGlobalRateLimiterStats().get(rateKey).getAcquireSuccessCount() > 0,
                "rate limiter stats must be non-zero before reset");
        assertTrue(taskFlowManager.getGlobalSemaphoreStats().get(rateKey).getAcquireCount() > 0,
                "semaphore stats must be non-zero before reset");

        taskFlowManager.resetGlobalStats();

        Map<String, IRateLimiter.RateLimiterStats> rateStats = taskFlowManager.getGlobalRateLimiterStats();
        assertEquals(0, rateStats.get(rateKey).getAcquireSuccessCount(),
                "resetGlobalStats must reset rate limiter stats. Pre-fix: only semaphores were reset.");
        Map<String, ISemaphore.SemaphoreStats> semStats = taskFlowManager.getGlobalSemaphoreStats();
        assertEquals(0, semStats.get(rateKey).getAcquireCount(),
                "semaphore stats reset must keep working");
    }

    @Test
    public void globalLimiter_paramMismatch_returnsCachedInstance() {
        TaskFlowManagerImpl taskFlowManager = new TaskFlowManagerImpl();
        ITaskRuntime taskRt = newTaskRuntime(taskFlowManager);

        IRateLimiter first = taskFlowManager.getRateLimiter(taskRt, "cfg-key", 100.0, true);
        IRateLimiter second = taskFlowManager.getRateLimiter(taskRt, "cfg-key", 200.0, true);
        assertSame(first, second, "global limiter is cached per key; mismatched params warn but do not rebuild");
        assertEquals(100.0, first.getPermitsPerSecond(),
                "cached instance keeps its initial configuration (documented frozen-config behavior)");

        ISemaphore semFirst = taskFlowManager.getSemaphore(taskRt, "cfg-sem", 2, true);
        ISemaphore semSecond = taskFlowManager.getSemaphore(taskRt, "cfg-sem", 3, true);
        assertSame(semFirst, semSecond, "global semaphore is cached per key");
        assertEquals(2, semFirst.maxPermits(),
                "cached instance keeps its initial configuration (documented frozen-config behavior)");
    }

    private static ITaskRuntime newTaskRuntime(TaskFlowManagerImpl taskFlowManager) {
        ITask task = taskFlowManager.getTask("test/sequential-01", 0);
        return taskFlowManager.newTaskRuntime(task, false, null);
    }
}
