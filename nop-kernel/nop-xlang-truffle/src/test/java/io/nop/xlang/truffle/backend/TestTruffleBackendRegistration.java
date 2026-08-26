package io.nop.xlang.truffle.backend;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.backend.EvalBackendDecision;
import io.nop.xlang.backend.EvalBackendRegistry;
import io.nop.xlang.backend.IEvalDynamicBackend;
import io.nop.xlang.exec.LiteralExecutable;
import io.nop.xlang.exec.PlusExecutable;
import io.nop.xlang.truffle.nodes.XLangRootNode;
import io.nop.xlang.truffle.runtime.XLangTruffleEngine;
import io.nop.xlang.truffle.translate.TranslationFailureEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.graalvm.polyglot.Engine;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * truffle 后端注册路径测试（Phase 2）：模块初始化显式注册 + Engine 探测（不可用条目不阻断
 * 启动）、池运行时接入冒烟（真实池求值 + 身份证据 artifact）、翻译失败消费者注册通道。
 */
public class TestTruffleBackendRegistration {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterEach
    public void reset() {
        // 测试注入的失败实例清理（不动缺省单例——其池跨测试复用是 SHARED 正确语义）
        EvalBackendRegistry registry = EvalBackendRegistry.instance();
        for (String id : Set.copyOf(registry.getBackendIds())) {
            if (!"truffle".equals(id))
                registry.unregister(registry.getBackend(id));
        }
    }

    @Test
    public void testInitializerRegistersBackendWithEngineProbe() {
        IEvalDynamicBackend registered = EvalBackendRegistry.instance().findDynamicBackend();
        assertSame(TruffleEvalExecutionBackend.instance(), registered);
        assertEquals("truffle", registered.getBackendId());
        assertTrue(registered.getCapabilities()
                .contains(io.nop.xlang.backend.EvalBackendCapability.DYNAMIC_TRANSLATION));
        // 注册时 Engine 探测成功（JVM 测试环境）→ 可用条目
        assertTrue(registered.isAvailable());
    }

    @Test
    public void testEngineInitFailureRegistersUnavailableEntry() {
        // 初始化失败注入（Supplier 缝）：不可用条目注册成功（不抛出、不阻断启动），保留原因
        TruffleEvalExecutionBackend failing = new TruffleEvalExecutionBackend(() -> {
            throw new IllegalStateException("injected engine failure");
        });

        EvalBackendRegistry registry = EvalBackendRegistry.instance();
        registry.unregister(TruffleEvalExecutionBackend.instance());
        try {
            failing.probeInitialization();
            registry.register(failing);

            assertFalse(failing.isAvailable());
            assertNotNull(failing.getUnavailableReason());
            assertTrue(failing.getUnavailableReason().contains("injected engine failure"));
            assertEquals(Collections.singletonMap("truffle", failing.getUnavailableReason()),
                    registry.getUnavailableBackends());

            // 决策树：动态路径 → 不可用条目 → 降级解释器（带观测）
            EvalBackendDecision decision = io.nop.xlang.backend.EvalBackendRouter.instance()
                    .decide("/dyn/failure.expr", null);
            assertEquals(EvalBackendDecision.RouteKind.INTERPRETER, decision.getKind());
            assertTrue(decision.isDegraded());
            assertTrue(decision.getReason().contains("injected engine failure"));
        } finally {
            registry.unregister(failing);
            registry.register(TruffleEvalExecutionBackend.instance());
        }
    }

    @Test
    public void testPoolRuntimeExecutionSmoke() {
        // 池运行时作为动态路径执行体：真实池求值 + 身份证据 artifact = 翻译 AST 根节点
        TruffleEvalExecutionBackend backend = TruffleEvalExecutionBackend.instance();
        SourceLocation loc = SourceLocation.fromPath("t:/backend/smoke.expr");
        IExecutableExpression tree = new PlusExecutable(loc,
                LiteralExecutable.build(loc, 40), LiteralExecutable.build(loc, 2));

        io.nop.xlang.backend.EvalBackendDynamicOutcome outcome = backend.executeDynamic(
                new io.nop.xlang.backend.EvalBackendDynamicRequest(
                        tree, new EvalRuntime(EvalExprProvider.newEvalScope()), null));

        assertFalse(outcome.isFallback());
        assertEquals(42, outcome.getValue());
        assertInstanceOf(XLangRootNode.class, outcome.getArtifact());
    }

    /**
     * check2 P1：翻译失败关联以 per-request 标志为准，不按 sourceKey 关联共享事件 map——
     * 同 sourceKey 存在陈旧事件时，真实求值错误（翻译成功、运行期异常）必须原样重抛，
     * 不得被误判为单元级翻译失败而降级重放（解释器重执行可能重复副作用）。
     */
    @Test
    public void testStaleFailureEventWithRealEvalErrorIsThrownNotFallback() {
        TruffleEvalExecutionBackend backend = new TruffleEvalExecutionBackend();
        try {
            String sourceKey = "t:/backend/stale-event.expr";
            // 种入陈旧翻译失败事件（并发窗口中同 sourceKey 的他方事件——旧实现的关联混淆源）
            backend.onTranslationFailure(new TranslationFailureEvent(
                    sourceKey, "StaleNode", SourceLocation.fromLine(sourceKey, 1), "stale-failure", null));

            // 翻译成功、运行期对象方法不存在 = 真实求值错误（MathHelper.divide 整数除零不抛，故用此形态）
            SourceLocation loc = SourceLocation.fromPath(sourceKey);
            IExecutableExpression tree = io.nop.xlang.exec.ObjFunctionExecutable.build(
                    loc, LiteralExecutable.build(loc, "receiver"), "noSuchMethod", false,
                    new IExecutableExpression[0]);

            RuntimeException ex = assertThrows(RuntimeException.class, () -> backend.executeDynamic(
                    new io.nop.xlang.backend.EvalBackendDynamicRequest(
                            tree, new EvalRuntime(EvalExprProvider.newEvalScope()), sourceKey)));
            // 真实求值错误原样上抛（异常本体即 NopEvalException），未被陈旧事件吞成降级
            assertTrue(ex instanceof io.nop.api.core.exceptions.NopEvalException
                            || ex.getCause() instanceof io.nop.api.core.exceptions.NopEvalException,
                    "real eval error must propagate unchanged: " + ex);
        } finally {
            backend.close();
        }
    }

    /**
     * check2 P2：close() 与惰性 pool() 的竞态——close() 不持锁时会在 pool() 开池期间读到
     * pool==null 直接返回，随后 pool() 把新建池赋给字段，已关闭的后端泄漏整个 Context 池。
     * synchronized close() 与 pool() 同锁后，close 等待开池完成再关闭新建池。
     */
    @Test
    public void testCloseDuringLazyPoolOpenDoesNotLeakPool() throws Exception {
        CountDownLatch engineEntered = new CountDownLatch(1);
        CountDownLatch engineGate = new CountDownLatch(1);
        TruffleEvalExecutionBackend backend = new TruffleEvalExecutionBackend(() -> {
            engineEntered.countDown();
            try {
                engineGate.await();
            } catch (InterruptedException e) {
                throw new IllegalStateException("engine supplier interrupted", e);
            }
            return XLangTruffleEngine.sharedEngine();
        });
        try {
            SourceLocation loc = SourceLocation.fromPath("t:/backend/close-race.expr");
            IExecutableExpression tree = new PlusExecutable(loc,
                    LiteralExecutable.build(loc, 1), LiteralExecutable.build(loc, 2));

            Thread evalThread = new Thread(() -> backend.executeDynamic(
                    new io.nop.xlang.backend.EvalBackendDynamicRequest(
                            tree, new EvalRuntime(EvalExprProvider.newEvalScope()), null)), "close-race-eval");
            evalThread.start();
            assertTrue(engineEntered.await(5, TimeUnit.SECONDS), "engine supplier must be entered");

            Thread closeThread = new Thread(backend::close, "close-race-close");
            closeThread.start();
            // 让 closeThread 进入 close()（修复后阻塞在 pool() 的监视器上；修复前立即返回）
            closeThread.join(300);

            engineGate.countDown();
            evalThread.join(5000);
            closeThread.join(5000);
            assertFalse(evalThread.isAlive(), "eval must complete");
            assertFalse(closeThread.isAlive(), "close must complete (no deadlock)");

            // 池字段必须为 null：close() 在 pool() 赋值后关闭了新建池。
            // 未同步的旧实现读到 null 直接返回，新建池永不关闭、字段残留非 null。
            Field poolField = TruffleEvalExecutionBackend.class.getDeclaredField("pool");
            poolField.setAccessible(true);
            assertNull(poolField.get(backend), "pool opened during the race must be closed by close()");
        } finally {
            backend.close();
        }
    }
}
