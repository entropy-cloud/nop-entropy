package io.nop.ai.core.routing;

import io.nop.ai.core.NopAiCoreErrors;
import io.nop.ai.core.NopAiCoreException;
import io.nop.autotest.junit.JunitBaseTestCase;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2026-08-15-0849-2 Phase 4: ConcurrencyRegistry 并发记账原语
 * （Minimum Rules #24 无静默跳过 + #25 新功能必有测试）。
 *
 * <p>语义契约：acquire/release 纯计数器原语；release 下溢 = acquire/release 不配对（编排缺陷）
 * → 显式 fail-fast（不静默钳制为 0）；主账号键 (provider, null) 与账号键互相独立；线程安全。
 */
public class TestConcurrencyRegistry extends JunitBaseTestCase {

    private final ConcurrencyRegistry registry = new ConcurrencyRegistry();

    @Test
    void acquireIncrementsAndCurrentCountReads() {
        assertEquals(1, registry.acquire("p1", null));
        assertEquals(2, registry.acquire("p1", null));
        assertEquals(2, registry.currentCount("p1", null));
    }

    @Test
    void releaseDecrements() {
        registry.acquire("p1", "key-a");
        registry.acquire("p1", "key-a");
        assertEquals(1, registry.release("p1", "key-a"));
        assertEquals(0, registry.release("p1", "key-a"));
        assertEquals(0, registry.currentCount("p1", "key-a"));
    }

    @Test
    void releaseUnderflowFailsFast() {
        // 计数已为 0 时重复 release：显式 fail-fast（裁定：acquire/release 不配对 = 编排缺陷）。
        NopAiCoreException e = assertThrows(NopAiCoreException.class, () -> registry.release("p1", "key-a"));
        assertEquals(NopAiCoreErrors.ERR_AI_AGENT_INVALID_ARG.getErrorCode(), e.getErrorCode());
        assertTrue(e.getMessage().contains("underflow"), "underflow must be identifiable in the message");
        // 下溢后计数保持 0（不进入负数状态）。
        assertEquals(0, registry.currentCount("p1", "key-a"));
    }

    @Test
    void mainAccountKeyAndAccountKeysAreIndependent() {
        registry.acquire("p1", null);
        registry.acquire("p1", "key-a");
        assertEquals(1, registry.currentCount("p1", null));
        assertEquals(1, registry.currentCount("p1", "key-a"));
        registry.release("p1", null);
        assertEquals(0, registry.currentCount("p1", null));
        assertEquals(1, registry.currentCount("p1", "key-a"), "account key count unaffected by main-account release");
    }

    @Test
    void nullProviderFailsFast() {
        assertThrows(NopAiCoreException.class, () -> registry.acquire(null, "key-a"));
        assertThrows(NopAiCoreException.class, () -> registry.currentCount(null, null));
    }

    @Test
    void concurrentAcquireReleaseIsThreadSafe() throws Exception {
        // 并发测试：8 线程 × 500 对 acquire/release 于同一键 → 最终计数 0（无丢失/无下溢）。
        int threads = 8;
        int pairs = 500;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger errors = new AtomicInteger();

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    for (int i = 0; i < pairs; i++) {
                        registry.acquire("p1", "key-c");
                        registry.release("p1", "key-c");
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS), "concurrent acquire/release must complete");
        pool.shutdown();

        assertEquals(0, errors.get(), "no thread may fail (underflow on matched pairs)");
        assertEquals(0, registry.currentCount("p1", "key-c"), "matched pairs must leave count at 0");
    }

    // ========================================================================
    // P2-REL：下溢补偿竞态（幽灵 +1）与计数表收缩
    // ========================================================================

    @Test
    void zeroCountEntriesAreRemovedFromMap() throws Exception {
        // 大量不同 key acquire/release 归零后，map 必须收缩（不再只增不删）。
        for (int i = 0; i < 100; i++) {
            String accountKey = "k" + i;
            assertEquals(1, registry.acquire("p1", accountKey));
            assertEquals(0, registry.release("p1", accountKey));
        }
        // 计数表规模回落（内部 map 无残留零计数键）。
        java.lang.reflect.Field field = ConcurrencyRegistry.class.getDeclaredField("counts");
        field.setAccessible(true);
        java.util.concurrent.ConcurrentMap<?, ?> counts =
                (java.util.concurrent.ConcurrentMap<?, ?>) field.get(registry);
        assertEquals(0, counts.size(), "zero-count entries must be removed (map must shrink)");
        // 收缩后：currentCount 语义不变（缺席 = 0），再次 acquire 正常工作。
        assertEquals(0, registry.currentCount("p1", "k0"));
        assertEquals(1, registry.acquire("p1", "k0"));
        assertEquals(1, registry.currentCount("p1", "k0"));
        assertEquals(0, registry.release("p1", "k0"));
    }

    @Test
    void concurrentMismatchedReleaseDoesNotLeakPhantomCount() throws Exception {
        // P2-REL 核心回归：旧实现"decrementAndGet 后 incrementAndGet 补偿"与并发 acquire
        // 竞争会永久 +1（幽灵计数）。新实现先比较后减 + per-key 原子临界区，无此窗口。
        // 混合负载：一半操作是配对的 acquire/release，一半是无匹配 release（下溢缺陷信号）。
        // 最终计数必须回到 0（每个 acquire 都被某个成功 release 抵消，且 release 永不为负）。
        int threads = 8;
        int pairs = 500;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger underflowErrors = new AtomicInteger();

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    for (int i = 0; i < pairs; i++) {
                        if ((i & 1) == 0) {
                            registry.acquire("p1", "key-d");
                            registry.release("p1", "key-d");
                        } else {
                            try {
                                registry.release("p1", "key-d");
                            } catch (NopAiCoreException e) {
                                underflowErrors.incrementAndGet();
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS), "concurrent mixed acquire/release must complete");
        pool.shutdown();

        assertEquals(0, registry.currentCount("p1", "key-d"),
                "no phantom +1 from the compensation race: count must return to 0");
        assertTrue(underflowErrors.get() >= 1,
                "unmatched releases (count already 0) must still fail fast");
    }
}
