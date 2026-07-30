package io.nop.metadata.service;

import io.nop.metadata.dao.entity.NopMetaQualityCheckpoint;
import io.nop.metadata.service.quality.CheckpointActionDispatcher;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 并发测试 {@link CheckpointActionDispatcher#dispatch}：多线程同时 dispatch 同一 checkpoint，
 * 验证 per-action try/catch 隔离在并发下仍正确（不串错误、不 panic、不 deadlock）。
 *
 * <p>共享状态验证：通过 {@link AtomicReference} 收集所有线程的 summary map，在全部线程完成后验证
 * 累计调度次数等于 threadCount × repeats，确保并发操作产生一致的最终状态。
 */
public class TestCheckpointActionDispatcherConcurrency {

    /** 4 线程 × 4 轮同时 dispatch（IHttpClient/IMessageService 均为 null，所有 action 走失败路径）。 */
    @Test
    public void testConcurrentDispatchWithNullDeps() throws Exception {
        int threadCount = 4;
        int repeats = 4;

        CheckpointActionDispatcher dispatcher = new CheckpointActionDispatcher(null, null);
        NopMetaQualityCheckpoint cp = cp();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger failures = new AtomicInteger(0);
        AtomicReference<List<Map<String, Object>>> allSummaries = new AtomicReference<>(Collections.synchronizedList(new ArrayList<>()));

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int r = 0; r < repeats; r++) {
                        Map<String, Object> s = summary();
                        dispatcher.dispatch(cp, s);
                        allSummaries.get().add(s);
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> errs = (List<Map<String, Object>>) s.get("errors");
                        if (errs.size() != 2) {
                            failures.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(15, TimeUnit.SECONDS), "all threads must finish within timeout");
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        assertEquals(0, failures.get(),
                "each dispatch must record exactly 2 errors (webhook + notify null-dep); no exceptions");

        // Shared-state verification: total dispatches must equal threadCount * repeats
        List<Map<String, Object>> collected = allSummaries.get();
        assertNotNull(collected, "collected summaries must not be null");
        assertEquals(threadCount * repeats, collected.size(),
                "total dispatches across all threads must equal " + (threadCount * repeats));
    }

    /** 8 线程全部 dispatch 同一 checkpoint，验证无死锁 + 共享状态一致性。 */
    @Test
    public void testConcurrentDispatchNoDeadlock() throws Exception {
        int threadCount = 8;
        CheckpointActionDispatcher dispatcher = new CheckpointActionDispatcher(null, null);
        NopMetaQualityCheckpoint cp = cp();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger failures = new AtomicInteger(0);
        AtomicReference<List<Map<String, Object>>> allSummaries = new AtomicReference<>(Collections.synchronizedList(new ArrayList<>()));

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    Map<String, Object> s = summary();
                    dispatcher.dispatch(cp, s);
                    allSummaries.get().add(s);
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> errs = (List<Map<String, Object>>) s.get("errors");
                    if (errs.size() != 2) {
                        failures.incrementAndGet();
                    }
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS), "all 8 threads must finish without deadlock");
        executor.shutdown();
        assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
        assertEquals(0, failures.get(), "no failures in concurrent dispatch");

        // Shared-state verification: total dispatches must equal threadCount
        List<Map<String, Object>> collected = allSummaries.get();
        assertNotNull(collected, "collected summaries must not be null");
        assertEquals(threadCount, collected.size(),
                "total dispatches across all threads must equal " + threadCount);
    }

    private NopMetaQualityCheckpoint cp() {
        NopMetaQualityCheckpoint cp = new NopMetaQualityCheckpoint();
        cp.setCheckpointId("cp-concurrency-test");
        cp.setActions("[{\"actionType\":\"webhook\",\"enabled\":true,"
                + "\"config\":{\"url\":\"http://mock/quality\"}},"
                + "{\"actionType\":\"notify\",\"enabled\":true,"
                + "\"config\":{\"channel\":\"test-channel\"}}]");
        return cp;
    }

    private static Map<String, Object> summary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("checkpointId", "cp-concurrency-test");
        summary.put("executedCount", 1);
        summary.put("errors", new ArrayList<>());
        return summary;
    }
}
