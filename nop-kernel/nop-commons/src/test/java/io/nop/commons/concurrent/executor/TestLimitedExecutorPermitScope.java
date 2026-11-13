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
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LimitedExecutor 许可作用域回归（CRA R1-04，plan 2499 Phase 2，裁定① nop-commons 本体半边 C3-007。
 * 设计权威来源：{@code ai-dev/design/concurrency/bindcancel-context-unification.md} §3.1）。
 *
 * <p>修复前红：acquire 抛 InterruptedException 即未获得许可，finally 仍 release →
 * 许可数虚增、并发上限被击穿（反复中断单调上涨）。修复后 release 移入 acquire 成功后作用域：
 * acquire 失败不补偿、提交被拒补偿。
 */
public class TestLimitedExecutorPermitScope {

    private static Executor threadPerTask() {
        return task -> {
            Thread thread = new Thread(task, "test-limited-executor-worker");
            thread.setDaemon(true);
            thread.start();
        };
    }

    /**
     * C3-007 修复前红：n=1 场景下中断驱动的幻影 release 使第二个任务与占用许可的任务并发执行。
     */
    @Test
    public void testInterruptedAcquireDoesNotInflatePermits() throws Exception {
        LimitedExecutor executor = LimitedExecutor.limit(threadPerTask(), 1);

        CountDownLatch t1Started = new CountDownLatch(1);
        CountDownLatch releaseT1 = new CountDownLatch(1);
        executor.execute(() -> {
            t1Started.countDown();
            try {
                releaseT1.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        assertTrue(t1Started.await(5, TimeUnit.SECONDS), "first task must hold the only permit");

        // 制造幻影 release：中断当前线程后 acquire 立即抛 InterruptedException
        Thread.currentThread().interrupt();
        try {
            assertThrows(NopException.class, () -> executor.execute(() -> {
            }));
        } finally {
            // 清除本测试注入的中断标志
            Thread.interrupted();
        }

        // n=1 必须仍然成立：t1 未释放许可前，新任务不得并发执行
        CountDownLatch t2Done = new CountDownLatch(1);
        Thread t2 = new Thread(() -> executor.execute(t2Done::countDown), "test-limited-executor-t2");
        t2.setDaemon(true);
        t2.start();
        Thread.sleep(150);
        assertEquals(1, t2Done.getCount(), "phantom release must not inflate permits: n=1 must be respected");

        releaseT1.countDown();
        assertTrue(t2Done.await(5, TimeUnit.SECONDS),
                "second task must run after the first task releases the permit");
    }

    /**
     * 语义钉：提交被拒（executor.execute 抛 REE，许可已获得）的补偿语义保持。
     */
    @Test
    public void testRejectionAfterAcquireStillCompensates() {
        AtomicBoolean first = new AtomicBoolean(true);
        java.util.concurrent.Executor failOnce = task -> {
            if (first.compareAndSet(true, false))
                throw new RejectedExecutionException("pool-rejected");
            task.run();
        };
        LimitedExecutor executor = LimitedExecutor.limit(failOnce, 1);

        assertThrows(RejectedExecutionException.class, () -> executor.execute(() -> {
        }));

        // 补偿后许可归还：后续提交可正常执行
        AtomicBoolean ran = new AtomicBoolean(false);
        executor.execute(() -> ran.set(true));
        assertTrue(ran.get(), "permit must be usable again after compensated rejection");
    }

    /**
     * 语义钉：acquire 失败（中断）路径不补偿、异常传播，且许可账目不变。
     */
    @Test
    public void testInterruptedAcquirePropagatesWithoutCompensation() throws Exception {
        LimitedExecutor executor = LimitedExecutor.limit(Runnable::run, 1);

        // 用占用许可证明中断路径没有多余 release：先占住唯一许可
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        Thread holder = new Thread(() -> executor.execute(() -> {
            started.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }), "test-limited-holder");
        holder.setDaemon(true);
        holder.start();
        assertTrue(started.await(5, TimeUnit.SECONDS));

        Thread.currentThread().interrupt();
        try {
            assertThrows(NopException.class, () -> executor.execute(() -> {
            }));
        } finally {
            Thread.interrupted();
        }

        // 中断路径未虚增许可：占用者未释放前，新任务仍被限流阻塞（不并发）
        CountDownLatch waiterDone = new CountDownLatch(1);
        Thread waiter = new Thread(() -> executor.execute(waiterDone::countDown), "test-limited-waiter");
        waiter.setDaemon(true);
        waiter.start();
        Thread.sleep(150);
        assertEquals(1, waiterDone.getCount(), "interrupted acquire must not grant a phantom permit");

        release.countDown();
        assertTrue(waiterDone.await(5, TimeUnit.SECONDS),
                "waiter must run after holder releases the permit");
    }
}
