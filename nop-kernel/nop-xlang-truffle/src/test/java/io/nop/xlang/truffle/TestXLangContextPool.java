package io.nop.xlang.truffle;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.DisabledEvalOutput;
import io.nop.core.lang.eval.EvalScopeImpl;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.exec.GuardNotNullExecutable;
import io.nop.xlang.exec.LiteralExecutable;
import io.nop.xlang.exec.NullExecutable;
import io.nop.xlang.truffle.eval.XLangTruffleEval;
import io.nop.xlang.truffle.lang.XLangLanguage;
import io.nop.xlang.truffle.runtime.XLangContextPool;
import io.nop.xlang.truffle.runtime.XLangTruffleEngine;
import io.nop.xlang.truffle.translate.TranslatedUnit;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Context 池租借协议正测试（plan I8 Phase 2 Exit Criteria）：租借→求值→归还→再租借无残留、
 * 异常路径归还后无残留、enter/leave 可重入批求值、共享 Engine 单例同一性（多池租借
 * Context 同一 Engine）、共享翻译缓存（跨 Context 同键同一翻译产物）+ SHARED 激活旗标、
 * 池耗尽阻塞语义、协议违约 fail-fast（双重归还/归还后求值/关闭后租借）。
 * 负测试（注入残留红/绿对照）在 {@code TestPoolProtocolNegative}。
 */
public class TestXLangContextPool {

    private static final SourceLocation LOC = SourceLocation.fromPath("/pool-protocol-test.xpl");

    private XLangContextPool pool;

    @AfterEach
    public void tearDown() {
        if (pool != null) {
            pool.close();
            pool = null;
        }
    }

    private static IExecutableExpression literal(int value) {
        return LiteralExecutable.build(LOC, value);
    }

    private static Object evalValue(XLangContextPool.Lease lease, String sourceKey,
                                    IExecutableExpression tree) {
        XLangTruffleEval.TranslatedEval result = lease.eval(sourceKey, tree, new EvalScopeImpl(),
                DisabledEvalOutput.INSTANCE);
        if (result.getThrown() != null) {
            if (result.getThrown() instanceof RuntimeException)
                throw (RuntimeException) result.getThrown();
            throw new IllegalStateException("unwrapped non-runtime thrown", result.getThrown());
        }
        return result.getReturnValue();
    }

    @Test
    public void testLeaseEvalReturnReLeaseNoResidue() {
        pool = XLangContextPool.open(2);
        for (int cycle = 0; cycle < 3; cycle++) {
            try (XLangContextPool.Lease lease = pool.lease()) {
                assertEquals(40 + cycle, evalValue(lease, "/pool-cycle.xpl", literal(40 + cycle)));
                assertEquals(1, pool.leasedCount(), "one outstanding lease during evaluation");
            }
            assertEquals(0, pool.leasedCount(), "lease must be returned: cycle=" + cycle);
        }
        assertEquals(2, pool.availableCount(), "all contexts must be back in the idle pool");
    }

    @Test
    public void testExceptionPathReturnNoResidue() {
        pool = XLangContextPool.open(1);
        IExecutableExpression thrower = new GuardNotNullExecutable(LOC, NullExecutable.NULL);
        for (int cycle = 0; cycle < 2; cycle++) {
            try (XLangContextPool.Lease lease = pool.lease()) {
                XLangTruffleEval.TranslatedEval result = lease.eval("/pool-throw.xpl", thrower,
                        new EvalScopeImpl(), DisabledEvalOutput.INSTANCE);
                assertNotNull(result.getThrown(), "guard-not-null tree must throw");
                // 异常路径归还：close 不抛（无残留）即本块通过（try-with-resources）
            }
            assertEquals(0, pool.leasedCount(), "exception path must still return the lease: cycle=" + cycle);
        }
        // 异常路径归还后的 Context 仍可用且无残留
        try (XLangContextPool.Lease lease = pool.lease()) {
            assertEquals(7, evalValue(lease, "/pool-after-throw.xpl", literal(7)));
        }
    }

    @Test
    public void testReentrantBatchEvalWithinSingleLease() {
        pool = XLangContextPool.open(1);
        try (XLangContextPool.Lease lease = pool.lease()) {
            // 可重入批求值：一个 enter..leave 窗口内多次 eval（每次一个 handoff 循环，不嵌套）
            assertEquals(1, evalValue(lease, "/pool-batch-a.xpl", literal(1)));
            assertEquals(2, evalValue(lease, "/pool-batch-b.xpl", literal(2)));
            assertEquals(3, evalValue(lease, "/pool-batch-c.xpl", literal(3)));
        }
        assertEquals(0, pool.leasedCount());
    }

    @Test
    public void testPoolExhaustionBlocksUntilReturn() throws Exception {
        pool = XLangContextPool.open(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        XLangContextPool.Lease first = pool.lease();
        try {
            assertEquals(11, evalValue(first, "/pool-block.xpl", literal(11)));
            CountDownLatch started = new CountDownLatch(1);
            AtomicReference<Object> secondResult = new AtomicReference<>();
            Future<?> waiter = executor.submit(() -> {
                started.countDown();
                try (XLangContextPool.Lease second = pool.lease()) {
                    secondResult.set(evalValue(second, "/pool-blocked.xpl", literal(22)));
                }
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            Thread.sleep(200);
            assertFalse(waiter.isDone(), "second borrower must block while the pool is exhausted");
            assertEquals(1, pool.leasedCount());
            first.close();
            waiter.get(10, TimeUnit.SECONDS);
            assertEquals(22, secondResult.get(), "blocked borrower must complete after the lease is returned");
        } finally {
            assertThrows(IllegalStateException.class, first::close, "double return must fail fast");
            executor.shutdownNow();
        }
    }

    @Test
    public void testDoubleReturnFailsFast() {
        pool = XLangContextPool.open(1);
        XLangContextPool.Lease lease = pool.lease();
        lease.close();
        assertThrows(IllegalStateException.class, lease::close,
                "double return is a protocol violation and must fail fast");
        assertEquals(0, pool.leasedCount());
    }

    @Test
    public void testEvalAfterReturnFailsFast() {
        pool = XLangContextPool.open(1);
        XLangContextPool.Lease lease = pool.lease();
        lease.close();
        assertThrows(IllegalStateException.class,
                () -> evalValue(lease, "/pool-after-close.xpl", literal(1)),
                "eval after return is a protocol violation and must fail fast");
    }

    @Test
    public void testPoolCloseFailsFastFurtherLease() {
        XLangContextPool closing = XLangContextPool.open(1);
        closing.close();
        assertThrows(IllegalStateException.class, closing::lease, "closed pool must not lease");
        assertThrows(IllegalStateException.class, closing::close, "double close must fail fast");
    }

    @Test
    public void testSharedEngineSingletonIdentityAcrossPooledContexts() {
        pool = XLangContextPool.open(3);
        Engine shared = XLangTruffleEngine.sharedEngine();
        for (int i = 0; i < 3; i++) {
            try (XLangContextPool.Lease lease = pool.lease()) {
                Context context = lease.getContext();
                assertSame(shared, context.getEngine(),
                        "every pooled context must be explicitly bound to the shared engine singleton");
            }
        }
    }

    @Test
    public void testSharedTranslationCacheAndMultipleContextsInitializedAcrossPooledContexts() {
        pool = XLangContextPool.open(3);
        TranslatedUnit first = null;
        for (int i = 0; i < 3; i++) {
            try (XLangContextPool.Lease lease = pool.lease()) {
                // 每轮独立实例、结构相同的树 → 同 sourceKey + 同树指纹 = 共享缓存命中同一翻译产物
                IEvalScope scope = new EvalScopeImpl();
                XLangTruffleEval.TranslatedEval result = lease.eval("/pool-shared-cache.xpl", literal(99),
                        scope, DisabledEvalOutput.INSTANCE);
                if (result.getThrown() != null)
                    throw new IllegalStateException("unexpected thrown", result.getThrown());
                assertEquals(99, result.getReturnValue());
                if (first == null) {
                    first = result.getUnit();
                } else {
                    assertSame(first, result.getUnit(),
                            "pooled contexts must share one language instance and one translation cache "
                                    + "(SHARED + explicit shared engine)");
                }
            }
        }
        assertNotNull(first, "at least one evaluation must have run");
        XLangLanguage language = first.getRootNode().getXLangLanguage();
        assertTrue(language.isMultipleContextsInitialized(),
                "framework must have invoked initializeMultipleContexts before sharing the language instance");
    }
}
