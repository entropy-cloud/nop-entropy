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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 并发测试 {@link CheckpointActionDispatcher#dispatch}：多线程同时 dispatch 同一 checkpoint，
 * 验证 per-action try/catch 隔离在并发下仍正确（不串错误、不 panic、不 deadlock）。
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

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int r = 0; r < repeats; r++) {
                        Map<String, Object> s = summary();
                        dispatcher.dispatch(cp, s);
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
    }

    /** 8 线程全部 dispatch 同一 checkpoint，验证无死锁。 */
    @Test
    public void testConcurrentDispatchNoDeadlock() throws Exception {
        int threadCount = 8;
        CheckpointActionDispatcher dispatcher = new CheckpointActionDispatcher(null, null);
        NopMetaQualityCheckpoint cp = cp();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger failures = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    Map<String, Object> s = summary();
                    dispatcher.dispatch(cp, s);
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
