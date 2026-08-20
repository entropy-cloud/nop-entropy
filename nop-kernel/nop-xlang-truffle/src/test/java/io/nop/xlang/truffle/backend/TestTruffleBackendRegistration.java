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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.graalvm.polyglot.Engine;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
}
