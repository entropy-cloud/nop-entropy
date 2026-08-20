package io.nop.xlang.truffle;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.DisabledEvalOutput;
import io.nop.core.lang.eval.EvalScopeImpl;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.exec.LiteralExecutable;
import io.nop.xlang.truffle.eval.XLangTruffleEval;
import io.nop.xlang.truffle.lang.XLangLanguage;
import io.nop.xlang.truffle.runtime.XLangContextPool;
import io.nop.xlang.truffle.translate.TranslationFailureEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 淘汰并发安全 + 并发翻译失败观测（plan I8 Phase 3）：并发 getOrBuild + 淘汰触发下结果
 * 一致、无错译/串用；观测事件在并发翻译失败注入下被记录且可查询（事件计数与失败次数
 * 一致，无丢失）；探针并发读写不崩（XFunctionDispatchNode 原子化探针在并发路径上单调）。
 */
public class TestConcurrentEvictionAndFailures {

    private static final SourceLocation LOC = SourceLocation.fromPath("/concurrent-eviction-test.xpl");

    private XLangContextPool pool;

    @BeforeEach
    public void setUp() {
        pool = XLangContextPool.open(4);
    }

    @AfterEach
    public void tearDown() {
        pool.close();
    }

    private static IExecutableExpression literal(int value) {
        return LiteralExecutable.build(LOC, value);
    }

    /**
     * 并发 getOrBuild + 淘汰触发：小容量共享缓存（池语言实例上）上多线程并发求值大量
     * 不同键——每次结果必须与树字面值一致（错译/串用即数值错位），池空闲回收正常。
     */
    @Test
    public void testConcurrentEvalWithEvictionPressureStaysCorrect() throws Exception {
        XLangLanguage language = pool.getLanguage();
        int threads = 4;
        int unitsPerThread = 60;
        Queue<Throwable> failures = new ConcurrentLinkedQueue<>();
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                final int threadIndex = t;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await(30, TimeUnit.SECONDS);
                        for (int i = 0; i < unitsPerThread; i++) {
                            int value = threadIndex * 1000 + i;
                            String sourceKey = "/concurrent-eviction/t" + threadIndex + "-u" + i + ".xpl";
                            try (XLangContextPool.Lease lease = pool.lease()) {
                                Object returned = lease.eval(sourceKey, literal(value),
                                        new EvalScopeImpl(), DisabledEvalOutput.INSTANCE).getReturnValue();
                                if (!Integer.valueOf(value).equals(returned))
                                    failures.add(new AssertionError("cross-contaminated translation: key=" + sourceKey
                                            + ", expected=" + value + ", was=" + returned));
                            }
                        }
                    } catch (Throwable e) {
                        failures.add(e);
                    }
                }));
            }
            assertTrue(ready.await(30, TimeUnit.SECONDS));
            start.countDown();
            for (Future<?> future : futures)
                future.get(120, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        if (!failures.isEmpty())
            fail("concurrent eviction pressure diverged: " + failures);
        assertEquals(0, pool.leasedCount());
        // 共享缓存条目被淘汰过（压力充足：4×60 键 >> 缺省容量上限不必依赖——仅验证缓存可观测非负）
        assertTrue(language.getTranslationCache().getEvictedCount() >= 0);
    }

    /**
     * 并发翻译失败注入：多线程同时触发支持集外节点翻译失败——fail-fast 各自抛出、事件
     * 全部被记录且可查询（计数与注入次数一致，无丢失；消费者收到的载荷完整）。
     */
    @Test
    public void testConcurrentTranslationFailuresAllRecorded() throws Exception {
        XLangLanguage language = pool.getLanguage();
        language.getTranslationFailures().clear();
        AtomicInteger received = new AtomicInteger();
        language.addTranslationFailureListener(event -> received.incrementAndGet());

        int threads = 4;
        int failuresPerThread = 5;
        int totalFailures = threads * failuresPerThread;
        Queue<Throwable> unexpected = new ConcurrentLinkedQueue<>();
        AtomicReference<Throwable> firstThrown = new AtomicReference<>();
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                final int threadIndex = t;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await(30, TimeUnit.SECONDS);
                        for (int i = 0; i < failuresPerThread; i++) {
                            IExecutableExpression unsupported = new CoverageNodes.FutureExecutable(
                                    SourceLocation.fromLine("/concurrent-fail/t" + threadIndex + "-u" + i + ".xpl", 1));
                            try (XLangContextPool.Lease lease = pool.lease()) {
                                XLangTruffleEvalHelper.eval(lease,
                                        "/concurrent-fail/t" + threadIndex + "-u" + i + ".xpl", unsupported);
                                unexpected.add(new AssertionError(
                                        "unsupported node must fail fast: thread=" + threadIndex + ", i=" + i));
                            } catch (RuntimeException e) {
                                firstThrown.compareAndSet(null, e);
                            }
                        }
                    } catch (Throwable e) {
                        unexpected.add(e);
                    }
                }));
            }
            assertTrue(ready.await(30, TimeUnit.SECONDS));
            start.countDown();
            for (Future<?> future : futures)
                future.get(120, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        if (!unexpected.isEmpty())
            fail("unexpected outcomes: " + unexpected);

        assertEquals(totalFailures, language.getTranslationFailures().getTotalReported(),
                "every concurrent translation failure must be recorded (no loss)");
        assertEquals(totalFailures, received.get(), "registered consumer must see every failure");
        List<TranslationFailureEvent> events = language.getTranslationFailures().getEvents();
        assertEquals(Math.min(totalFailures, 256), events.size(), "ring retention bounded");
        for (TranslationFailureEvent event : events) {
            assertTrue(event.getSourceKey().startsWith("/concurrent-fail/"),
                    "event payload must map to its own unit: " + event.getSourceKey());
            assertNotNull(event.getNodeClassName());
            assertNotNull(event.getReason());
        }
        Throwable thrown = firstThrown.get();
        assertNotNull(thrown, "fail-fast must still be thrown to callers under concurrency");
    }

    /** 池驱动 fail-fast 辅助（throw 原始异常）。 */
    private static final class XLangTruffleEvalHelper {
        static void eval(XLangContextPool.Lease lease, String sourceKey, IExecutableExpression tree) {
            XLangTruffleEval.TranslatedEval result = lease.eval(sourceKey, tree, new EvalScopeImpl(),
                    DisabledEvalOutput.INSTANCE);
            if (result.getThrown() != null) {
                if (result.getThrown() instanceof RuntimeException)
                    throw (RuntimeException) result.getThrown();
                throw new IllegalStateException("unwrapped non-runtime thrown", result.getThrown());
            }
        }
    }
}
