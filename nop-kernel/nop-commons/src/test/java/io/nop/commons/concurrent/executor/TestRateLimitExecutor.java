/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.concurrent.executor;

import io.nop.commons.test.time.ManualClock;
import io.nop.commons.test.time.ManualScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 时间确定化（plan 2513-WI7）：RateLimitExecutorImpl 本身不读时钟，节流窗口的到期由构造注入的
 * IScheduledExecutor 决定——测试注入与 {@link ManualClock} 共用虚拟时间线的 ManualScheduler，
 * 由测试显式推进触发调度任务；「窗口内被跳过」以待执行任务计数为确定性证据。
 */
public class TestRateLimitExecutor {
    private ManualClock clock;
    private ManualScheduler scheduler;
    private RateLimitExecutorImpl limiter;

    @BeforeEach
    public void setUp() {
        clock = new ManualClock(1_000_000L);
        scheduler = new ManualScheduler(clock);
        limiter = new RateLimitExecutorImpl(new ManualScheduledExecutor(scheduler));
    }

    @AfterEach
    public void tearDown() {
        if (scheduler != null)
            scheduler.shutdown();
    }

    @Test
    public void testThrottle() {
        AtomicInteger count = new AtomicInteger();

        // 第一次调用触发调度：窗口已挂起但任务未执行
        limiter.throttle("k", 100, count::incrementAndGet);
        assertEquals(1, scheduler.pendingTaskCount(), "throttle must arm exactly one scheduled task");
        assertEquals(0, count.get(), "task must not run before the throttle window elapses");

        // 推进虚拟时间越过窗口：调度的任务在推进线程上执行
        scheduler.advance(100);
        assertEquals(1, count.get());

        // 首个任务已执行完毕后，新的节流窗口应重新生效：
        // pending窗口内的其他throttle调用应被跳过
        limiter.throttle("k", 150, count::incrementAndGet);
        assertEquals(1, scheduler.pendingTaskCount(), "new throttle call must re-arm the window");
        limiter.throttle("k", 150, count::incrementAndGet);
        limiter.throttle("k", 150, count::incrementAndGet);
        assertEquals(1, scheduler.pendingTaskCount(),
                "calls inside the pending window must be skipped, not scheduled");

        // 整个过程只执行2次任务：第二次调度的任务执行，pending窗口内的两次调用被跳过
        scheduler.advance(150);
        assertEquals(2, count.get());
    }

    @Test
    public void testThrottleFailureCleansUpWindow() throws Exception {
        limiter.throttle("k", 100, () -> {
            throw new IllegalStateException("task failed");
        });
        scheduler.advance(100);

        // 失败任务结束后条目应被清理，同 key 可以重新节流
        assertFalse(getPromiseMap().containsKey("k"), "failed task must clean up its window entry");
        AtomicInteger count = new AtomicInteger();
        limiter.throttle("k", 100, count::incrementAndGet);
        assertEquals(1, scheduler.pendingTaskCount(), "failed window must be cleaned up and re-armable");
        scheduler.advance(100);
        assertEquals(1, count.get(), "re-armed throttle task must run after its window");
    }

    @Test
    public void testDebounce() throws Exception {
        AtomicInteger count = new AtomicInteger();

        limiter.debounce("k", 50, count::incrementAndGet);
        assertEquals(1, scheduler.pendingTaskCount(), "debounce must arm one scheduled task");

        // debounce：窗口内的新调用取消旧任务后重新调度
        limiter.debounce("k", 50, count::incrementAndGet);
        assertEquals(1, scheduler.pendingTaskCount(), "rescheduled debounce must keep exactly one task");

        scheduler.advance(50);
        assertEquals(1, count.get(), "only the last debounced task must run");

        // debounce任务执行完毕后条目应被清理，不应残留在map中
        assertFalse(getPromiseMap().containsKey("k"));
    }

    @Test
    public void testThrottleCleanup() throws Exception {
        AtomicInteger count = new AtomicInteger();
        limiter.throttle("k", 50, count::incrementAndGet);
        scheduler.advance(50);
        assertEquals(1, count.get());

        // throttle任务执行完毕后条目应被清理
        assertFalse(getPromiseMap().containsKey("k"));
    }

    @SuppressWarnings("unchecked")
    private ConcurrentMap<Object, ? extends Future<?>> getPromiseMap() throws Exception {
        Field field = RateLimitExecutorImpl.class.getDeclaredField("promiseMap");
        field.setAccessible(true);
        return (ConcurrentMap<Object, ? extends Future<?>>) (Map<?, ?>) field.get(limiter);
    }

    /**
     * 将 ManualScheduler 适配为 RateLimitExecutorImpl 需要的 IScheduledExecutor。
     * debounce 依赖返回 Future 的 cancel 取消旧任务，故取消操作透传到调度器内部任务。
     */
    static class ManualScheduledExecutor implements IScheduledExecutor {
        private final ManualScheduler scheduler;

        ManualScheduledExecutor(ManualScheduler scheduler) {
            this.scheduler = scheduler;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <V> CompletableFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            Future<V>[] ref = new Future[1];
            CompletableFuture<V> promise = new CompletableFuture<V>() {
                @Override
                public boolean cancel(boolean mayInterruptIfRunning) {
                    boolean cancelled = super.cancel(mayInterruptIfRunning);
                    if (ref[0] != null)
                        ref[0].cancel(mayInterruptIfRunning);
                    return cancelled;
                }
            };
            ref[0] = scheduler.schedule(() -> {
                try {
                    V value = callable.call();
                    if (!promise.isCancelled())
                        promise.complete(value);
                    return value;
                } catch (Throwable t) {
                    promise.completeExceptionally(t);
                    if (t instanceof Exception)
                        throw (Exception) t;
                    throw new IllegalStateException(t);
                }
            }, delay, unit);
            return promise;
        }

        @Override
        public Future<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            return scheduler.scheduleAtFixedRate(command, initialDelay, period, unit);
        }

        @Override
        public Future<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            return scheduler.scheduleWithFixedDelay(command, initialDelay, delay, unit);
        }

        @Override
        public void execute(Runnable command) {
            scheduler.execute(command);
        }

        @Override
        public <V> CompletableFuture<V> submit(Callable<V> callable) {
            CompletableFuture<V> promise = new CompletableFuture<>();
            scheduler.execute(() -> {
                try {
                    promise.complete(callable.call());
                } catch (Throwable t) {
                    promise.completeExceptionally(t);
                }
            });
            return promise;
        }

        @Override
        public <V> CompletableFuture<V> submit(Runnable task, V result) {
            CompletableFuture<V> promise = new CompletableFuture<>();
            scheduler.execute(() -> {
                try {
                    task.run();
                    promise.complete(result);
                } catch (Throwable t) {
                    promise.completeExceptionally(t);
                }
            });
            return promise;
        }

        @Override
        public String getName() {
            return "manual-scheduler";
        }

        @Override
        public ThreadPoolConfig getConfig() {
            return new ThreadPoolConfig();
        }

        @Override
        public ThreadPoolStats stats() {
            return new ThreadPoolStats();
        }

        @Override
        public void refreshConfig() {
            // 手动调度器无动态配置
        }

        @Override
        public void destroy() {
            scheduler.shutdown();
        }

        @Override
        public boolean isDestroyed() {
            return scheduler.isShutdown();
        }
    }

    /**
     * plan 2547 R4-13（C1-004）pin0104a：同一 key 连续两次 debounce → 只执行最后一次
     * （debounce 的核心契约）。虚拟时间确定化形态（合并修订 2026-11-13：原真实 sleep
     * 版本与本类 ManualScheduler fixture 不兼容——虚拟时间线不推进则真实 latch 永不倒计）。
     * <p>
     * closure audit R1 B-1/M-6 勘误语义保留：调度可被 cancel（bindCancel），首版实现下
     * 本钉必红（两次都执行 / 第二次丢失）。
     */
    @Test
    public void pin0104a_debounceExecutesOnlyLastTask() throws Exception {
        AtomicInteger executed = new AtomicInteger();

        limiter.debounce("k-debounce", 250, () -> executed.incrementAndGet());
        assertEquals(1, scheduler.pendingTaskCount(), "first debounce arms one task");
        limiter.debounce("k-debounce", 250, () -> executed.incrementAndGet());
        assertEquals(1, scheduler.pendingTaskCount(), "rescheduled debounce keeps exactly one task");

        scheduler.advance(250);
        assertEquals(1, executed.get(),
                "debounce must execute only the last task (defect/first-version=2), actual=" + executed.get());
        assertFalse(getPromiseMap().containsKey("k-debounce"), "entry must be cleaned after fire");
    }

    /** pin0104a2：并发 debounce 同一 key 后 map 只保留一个条目（非原子缺陷面）。 */
    @Test
    public void pin0104a2_concurrentDebounceKeepsSingleEntry() throws Exception {
        int n = 8;
        CountDownLatch all = new CountDownLatch(1);
        java.util.List<Thread> threads = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            Thread t = new Thread(() -> {
                try {
                    all.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                limiter.debounce("k1", 200, () -> {
                });
            });
            threads.add(t);
            t.start();
        }
        all.countDown();
        for (Thread t : threads)
            t.join(5000);

        java.lang.reflect.Field f = RateLimitExecutorImpl.class.getDeclaredField("promiseMap");
        f.setAccessible(true);
        java.util.Map<?, ?> map = (java.util.Map<?, ?>) f.get(limiter);
        assertEquals(1, map.size(), "并发去抖后 map 中同 key 只应有一个条目，实际=" + map.size());
    }

    /**
     * plan 2547 R4-13（C1-004）pin0104b：delay=0 时任务结束后 promiseMap 无陈旧条目。
     * 缺陷态：holder[0] 提交后写，delay=0 时定时线程读到 null → CHM.remove(key,null) NPE 被吞，
     * 条目永久残留。虚拟时间确定化形态（合并修订 2026-11-13，理由同 pin0104a）。
     */
    @Test
    public void pin0104b_noStaleEntryAfterZeroDelayTask() throws Exception {
        limiter.debounce("k0", 0, () -> { });
        scheduler.advance(0);

        // 再触发一次，验证不会被上一次的陈旧条目错误取消
        AtomicInteger second = new AtomicInteger();
        limiter.debounce("k0", 0, second::incrementAndGet);
        scheduler.advance(0);
        assertEquals(1, second.get(), "second debounce must execute normally");
        assertFalse(getPromiseMap().containsKey("k0"), "no stale entry after zero-delay debounce");
    }
}
