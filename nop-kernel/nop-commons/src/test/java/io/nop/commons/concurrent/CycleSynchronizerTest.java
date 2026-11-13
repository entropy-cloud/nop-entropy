/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2547 R4-13（C1-010 + C2-004）CycleSynchronizer 载体钉。
 */
public class CycleSynchronizerTest {

    /**
     * pin0110（closure audit R1 B-2 重做）：**旧周期的延迟收尾不得清掉新周期状态**。
     * <p>
     * 真交错形态：周期 1 begin → 新周期 begin（周期 2）→ 用「周期 1 的代号快照」发起收尾。
     * 有 generation 守卫时该收尾被忽略，新周期仍 processing；无守卫时被清掉（本钉必红）。
     * <p>
     * 为构造「旧代收尾」而在测试内经由包级可见的语义：{@code finish()} 会先快照当前代号再收尾，
     * 故这里用「先取快照 → begin 新周期 → 以旧快照收尾」的等价序列由 {@code resolvePromise}
     * 的 expectedGeneration 参数承载；由于该参数由 {@code finish()} 内部生成，测试改用反射直接
     * 调用 {@code resolvePromise(false, oldGeneration)} 模拟延迟收尾。
     */
    @Test
    public void pin0110_staleFinishDoesNotClobberNewCycle() throws Exception {
        CycleSynchronizer sync = new CycleSynchronizer();
        assertTrue(sync.beginCycle());
        long oldGeneration = currentGeneration(sync);

        // 新周期开始（代号递增）
        sync.completeCycle();          // 正常收尾周期 1
        assertFalse(sync.isProcessing());
        assertTrue(sync.beginCycle()); // 周期 2
        assertTrue(sync.isProcessing());

        // 模拟「周期 1 的延迟收尾」：用旧代号调用 resolvePromise
        invokeResolve(sync, false, oldGeneration);

        assertTrue(sync.isProcessing(),
                "旧周期的延迟收尾不得清掉新周期的 processing（无 generation 守卫时会失败）");
    }

    private static long currentGeneration(CycleSynchronizer sync) throws Exception {
        java.lang.reflect.Field f = CycleSynchronizer.class.getDeclaredField("cycleGeneration");
        f.setAccessible(true);
        return f.getLong(sync);
    }

    private static void invokeResolve(CycleSynchronizer sync, boolean finished, long generation) throws Exception {
        java.lang.reflect.Method m = CycleSynchronizer.class
                .getDeclaredMethod("resolvePromise", boolean.class, long.class);
        m.setAccessible(true);
        m.invoke(sync, finished, generation);
    }

    /** pin0110b：processing 期间 beginCycle 抛并发错误（守卫未回归）。 */
    @Test
    public void pin0110b_beginWhileProcessingRejected() {
        CycleSynchronizer sync = new CycleSynchronizer();
        assertTrue(sync.beginCycle());
        try {
            sync.beginCycle();
            org.junit.jupiter.api.Assertions.fail("processing 期间重复 beginCycle 必须抛错");
        } catch (io.nop.api.core.exceptions.NopException e) {
            assertEquals("nop.err.concurrent.cycle-already-begin", e.getErrorCode());
        }
    }

    /** pin0204a：回调内等待本周期 promise 不得死锁（回调已移出锁域 + 先 complete 再回调）。 */
    @Test
    public void pin0204a_callbackCanAwaitCyclePromiseWithoutDeadlock() throws Exception {
        CycleSynchronizer sync = new CycleSynchronizer();
        sync.beginCycle();

        CountDownLatch callbackRan = new CountDownLatch(1);
        CompletableFuture<Boolean> waited = sync.waitCycleCompleted(() -> callbackRan.countDown());
        assertTrue(waited != null);

        // 在另一个线程触发收尾，避免测试线程自己阻塞
        Thread finisher = new Thread(sync::completeCycle);
        finisher.start();
        finisher.join(5000);

        assertTrue(callbackRan.await(5, TimeUnit.SECONDS), "回调必须被执行（出锁域后）");
        assertTrue(waited.isDone(), "promise 必须已完成");
    }

    /** pin0204b：单个回调抛异常不阻断后续回调、不以异常完成 promise。 */
    @Test
    public void pin0204b_callbackExceptionIsolated() {
        CycleSynchronizer sync = new CycleSynchronizer();
        sync.beginCycle();

        List<String> order = new CopyOnWriteArrayList<>();
        AtomicInteger secondRan = new AtomicInteger();
        CompletableFuture<Boolean> p = sync.waitCycleCompleted(() -> {
            order.add("first");
            throw new IllegalStateException("canned-callback-failure");
        });
        sync.waitCycleCompleted(() -> {
            secondRan.incrementAndGet();
            order.add("second");
        });

        sync.completeCycle();

        assertEquals(List.of("first", "second"), order, "第二个回调必须仍然执行（逐回调隔离）");
        assertEquals(1, secondRan.get());
        assertTrue(p.isDone());
        assertFalse(p.isCompletedExceptionally(), "回调异常不得把 promise 变成异常完成");
        assertEquals(Boolean.FALSE, p.join());
    }
}
