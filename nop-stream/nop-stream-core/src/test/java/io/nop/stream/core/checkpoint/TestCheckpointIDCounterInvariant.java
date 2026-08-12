/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint;

import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate ③ — CheckpointIDCounter 更新原子性 / 恢复后单调性（invariant #3）.
 *
 * <p>参数化并发测试：固定线程数 × 固定迭代数，多线程 {@link CheckpointIDCounter#getAndIncrement()}
 * 无重复、无丢失（结果集 == 区间 [start, start+N*M) 恰一次）；恢复路径 {@code set} 后继续递增
 * （无 ID 倒退）。覆盖 I0 定稿的原子性不变式（历史证据：R16-AR-5 恢复后 ID 从 0 开始 → 覆写旧
 * checkpoint；live 修复点 CheckpointCoordinator:896-900 单调推进）。
 */
public class TestCheckpointIDCounterInvariant {

    static Stream<Object[]> threadIterationCombos() {
        // (threads, iterations) — 覆盖并发压力与总量级
        return Stream.of(
                new Object[]{2, 500},
                new Object[]{4, 500},
                new Object[]{8, 250},
                new Object[]{16, 100});
    }

    /**
     * 多线程 getAndIncrement：结果集必须恰好覆盖 [start, start+threads*iterations) 无重复无缺口。
     */
    @ParameterizedTest
    @MethodSource("threadIterationCombos")
    void testConcurrentGetAndIncrementHasNoDuplicatesAndNoGaps(int threads, int iterations) throws Exception {
        long start = 1000L;
        CheckpointIDCounter counter = new CheckpointIDCounter(start);
        int total = threads * iterations;
        Set<Long> results = java.util.Collections.synchronizedSet(new TreeSet<>());

        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                ready.countDown();
                go.await();
                for (int i = 0; i < iterations; i++) {
                    results.add(counter.getAndIncrement());
                }
                return null;
            });
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS), "threads must start");
        go.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "workers must finish");

        assertEquals(total, results.size(), "no duplicates and no gaps: unique ids must equal total draws");
        long expectedMin = start;
        long expectedMax = start + total - 1;
        assertEquals(expectedMin, results.stream().mapToLong(Long::longValue).min().orElse(-1));
        assertEquals(expectedMax, results.stream().mapToLong(Long::longValue).max().orElse(-1));
        LongStream.range(expectedMin, expectedMax + 1).forEach(id ->
                assertTrue(results.contains(id), "gap detected: id " + id + " missing"));
        assertEquals(start + total, counter.get(), "counter must advance exactly total steps");
    }

    /**
     * 恢复路径：set(restoredId) 后计数器从 restoredId 继续递增（无跳跃、无倒退）。
     * 单调守卫（restoredId >= current 才推进）是 CheckpointCoordinator:896-900 的契约，
     * 本门禁验证 CheckpointIDCounter 的 set+递增机制本身：set(v) → getAndIncrement()==v →
     * 后续严格 +1 单调。
     */
    @ParameterizedTest
    @MethodSource("threadIterationCombos")
    void testRestoreSetThenContinueMonotonic(int threads, int iterations) {
        CheckpointIDCounter counter = new CheckpointIDCounter();
        // 先并发推进一段，模拟运行期
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                for (int i = 0; i < iterations; i++) {
                    counter.getAndIncrement();
                }
                return null;
            });
        }
        pool.shutdown();
        try {
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        long current = counter.get();

        // 恢复语义：set(restoredId) 后从 restoredId 继续（单调推进 restoredId+1 是 coordinator 职责）
        long restored = current + 500;
        counter.set(restored);
        assertEquals(restored, counter.getAndIncrement(), "set() must be followed by getAndIncrement()==restoredId");
        assertEquals(restored + 1, counter.getAndIncrement(), "must continue monotonically after restore");
        assertEquals(restored + 2, counter.get(), "counter must advance monotonically after restore");
    }
}
