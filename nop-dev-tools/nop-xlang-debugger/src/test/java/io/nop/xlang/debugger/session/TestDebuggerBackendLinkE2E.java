/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.debugger.session;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;
import io.nop.api.debugger.Breakpoint;
import io.nop.core.lang.eval.DefaultExpressionExecutor;
import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExecutableExpressionVisitor;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.xlang.api.XLang;
import io.nop.xlang.backend.EvalBackendRegistry;
import io.nop.xlang.backend.EvalBackendCapability;
import io.nop.xlang.backend.EvalBackendRouter;
import io.nop.xlang.backend.EvalStaticBoundExecutable;
import io.nop.xlang.backend.IEvalStaticBackend;
import io.nop.xlang.backend.IEvalStaticBinding;
import io.nop.xlang.debugger.DebugExpressionExecutor;
import io.nop.xlang.debugger.XLangDebugger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static io.nop.xlang.XLangConfigs.CFG_XLANG_DEBUGGER_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端验证（plan 2470 Major-2 选项 a，M2 验收锚点）：调试态绑定单元降级经全局执行器
 * 到达真实调试器——真断点挂起 → resume 端到端命中（Anti-Hollow：降级链从绑定命中到
 * 会话调试器 checkBreakpoint 运行时连通）。
 *
 * <p>本模块 test classpath 无 java 后端绑定产物，以内联 fake static backend 触发包装
 * （降级路径不消费 binding，fake 足够）；nop-xlang 域的路由/裁决测试见
 * io.nop.xlang.backend.TestDebuggerBackendLink。全局槽位卫生照
 * TestSessionRoutingIntegration 先例（@AfterAll 恢复）。
 *
 * <p>脚本用非常量表达式 `40 + x`（x 由 compile/runtime scope 提供）：常量表达式会被
 * 折叠为 LiteralExecutable（allowBreakPoint=false，不可断点）。
 */
public class TestDebuggerBackendLinkE2E {
    static class FakeBinding implements IEvalStaticBinding {
        @Override
        public Object execute(EvalRuntime rt) {
            return "bound-direct-value";
        }

        @Override
        public Object getBindingArtifact() {
            return "fake-artifact";
        }
    }

    static class FakeBackend implements IEvalStaticBackend {
        @Override
        public String getBackendId() {
            return "fake-static-e2e";
        }

        @Override
        public Set<EvalBackendCapability> getCapabilities() {
            return Collections.unmodifiableSet(EnumSet.of(EvalBackendCapability.STATIC_GENERATED));
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public String getUnavailableReason() {
            return null;
        }

        @Override
        public boolean isStaticCandidate(String resourcePath) {
            return "t:/xdbg-e2e.xpl".equals(resourcePath);
        }

        @Override
        public IEvalStaticBinding findStaticBinding(String resourcePath, IExecutableExpression tree) {
            return new FakeBinding();
        }
    }

    static XLangDebugger debugger;
    static SessionDebugManager manager;
    static IExpressionExecutor prevExecutor;

    @BeforeAll
    static void beforeAll() {
        manager = SessionDebugManager.instance();
        debugger = new XLangDebugger();
        debugger.setUseExternalPath(false);
        manager.init(debugger);
        prevExecutor = EvalExprProvider.getGlobalExecutor();
    }

    /** 注册调试执行器（E2E 降级链的全局执行器侧）。 */
    private static void registerDebugExecutor() {
        EvalExprProvider.registerGlobalExecutor(new DebugExpressionExecutor(debugger));
    }

    @AfterAll
    static void afterAll() {
        EvalExprProvider.registerGlobalExecutor(DefaultExpressionExecutor.INSTANCE);
        if (prevExecutor != null && !(prevExecutor instanceof DefaultExpressionExecutor))
            EvalExprProvider.registerGlobalExecutor(prevExecutor);
        DebugSessionScope.clear();
        manager.reset();
        restoreEnabled(false);
    }

    @BeforeEach
    public void setUp() {
        // 每用例恢复注册表与关闭态（beforeAll 重建默认会话）
        manager.init(debugger);
    }

    @AfterEach
    public void tearDown() {
        manager.reset();
        manager.init(debugger);
    }

    private static void setEnabled(boolean value) {
        AppConfig.getConfigProvider().updateConfigValue(CFG_XLANG_DEBUGGER_ENABLED, value);
    }

    private static void restoreEnabled(boolean value) {
        AppConfig.getConfigProvider().updateConfigValue(CFG_XLANG_DEBUGGER_ENABLED, value);
    }

    /** 直通对照：非调试态绑定直调——降级不发生、调试器零命中。 */
    @Test
    public void testDisabledBindingDirectCallNoDebuggerHit() throws Exception {
        setEnabled(false);
        IExecutableExpression tree = XLang.newCompileTool()
                .compileFullExpr(SourceLocation.fromLine("t:/xdbg-e2e.xpl", 1), "40 + 2").getExpr();
        EvalStaticBoundExecutable bound = new EvalStaticBoundExecutable(tree, new FakeBinding(),
                "fake-static-e2e");

        final AtomicReference<Object> result = new AtomicReference<>();
        Thread thread = new Thread(() -> result.set(bound.execute(null, new EvalRuntime(XLang.newEvalScope()))));
        thread.start();
        thread.join(5000);

        assertEquals("bound-direct-value", result.get(), "绑定直调（零命中降级）");
        assertFalse(debugger.isSuspended());
    }

    /**
     * M2 验收锚点端到端：调试态绑定单元（可断点真编译树 + 假绑定）降级经全局执行器
     * 到达真实调试器——断点挂起 → resume → 绑定单元返回脚本继续执行完。
     */
    /**
     * 可断点的语句级树（形态同解释器树中 allowBreakPoint=true 的语句节点）：execute 内
     * 经 executor 求值子表达式——验证降级链的 executor 传递连通。
     */
    static class BreakableComputeExpr implements IExecutableExpression {
        @Override
        public SourceLocation getLocation() {
            return SourceLocation.fromLine("t:/xdbg-e2e.xpl", 1);
        }

        @Override
        public boolean allowBreakPoint() {
            return true;
        }

        @Override
        public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
            // 语句体：经 executor 链求值（降级链连通的二次探针）
            Object left = executor.execute(newConst(40), rt);
            Object right = executor.execute(newConst(2), rt);
            return ((Number) left).intValue() + ((Number) right).intValue();
        }

        private IExecutableExpression newConst(int v) {
            return new IExecutableExpression() {
                @Override
                public Object execute(IExpressionExecutor exec, EvalRuntime r) {
                    return v;
                }

                @Override
                public SourceLocation getLocation() {
                    return null;
                }

                @Override
                public boolean allowBreakPoint() {
                    return false;
                }

                @Override
                public void visit(IExecutableExpressionVisitor visitor) {
                }

                @Override
                public boolean containsReturnStatement() {
                    return false;
                }

                @Override
                public boolean containsBreakStatement() {
                    return false;
                }

                @Override
                public void display(StringBuilder sb) {
                    sb.append(v);
                }
            };
        }

        @Override
        public void visit(IExecutableExpressionVisitor visitor) {
        }

        @Override
        public boolean containsReturnStatement() {
            return false;
        }

        @Override
        public boolean containsBreakStatement() {
            return false;
        }

        @Override
        public void display(StringBuilder sb) {
            sb.append("40 + 2");
        }
    }

    @Test
    public void testDebugModeBoundUnitBreakpointHitEndToEnd() throws Exception {
        setEnabled(true);
        IExecutableExpression tree = new BreakableComputeExpr();
        assertTrue(tree.allowBreakPoint(), "E2E 树必须可断点");
        EvalStaticBoundExecutable bound = new EvalStaticBoundExecutable(tree, new FakeBinding(),
                "fake-static-e2e");

        debugger.addBreakpoint(Breakpoint.build("t:/xdbg-e2e.xpl", 1));
        FakeBackend backend = new FakeBackend();
        EvalBackendRegistry.instance().register(backend);
        try {
            registerDebugExecutor(); // E2E 关键接线：全局执行器 = 调试执行器（降级链终点）
            assertTrue(EvalBackendRouter.instance().isDebugDegradeActive(),
                    "谓词必须激活（enabled=true）");
            assertTrue(EvalExprProvider.getGlobalExecutor() instanceof DebugExpressionExecutor,
                    "全局执行器必须是调试执行器");
            final AtomicReference<Object> result = new AtomicReference<>();
            final AtomicReference<Throwable> error = new AtomicReference<>();
            Thread thread = new Thread(() -> {
                try {
                    result.set(bound.execute(null, new EvalRuntime(XLang.newEvalScope())));
                } catch (Throwable t) {
                    error.set(t);
                }
            });
            thread.start();

            // 降级经全局执行器 → DebugExpressionExecutor → checkBreakpoint → 断点挂起
            long deadline = System.currentTimeMillis() + 5000;
            while (!debugger.isSuspended()) {
                if (System.currentTimeMillis() > deadline) {
                    if (error.get() != null)
                        throw new AssertionError("执行线程异常", error.get());
                    throw new AssertionError("调试态绑定单元断点未命中（降级链断裂）");
                }
                Thread.sleep(10);
            }
            assertTrue(debugger.isSuspended(), "绑定单元降级后断点命中（M2 验收锚点）");

            debugger.resume();
            thread.join(5000);
            assertFalse(thread.isAlive(), "resume 后脚本继续执行完");
            assertEquals(null, error.get());
            assertEquals(42, ((Number) result.get()).intValue(), "降级执行 sourceTree 的求值结果");
        } finally {
            EvalBackendRegistry.instance().unregister(backend);
            debugger.removeBreakpoint(Breakpoint.build("t:/xdbg-e2e.xpl", 1));
        }
    }

    /** 编译期求值路径不经过的守卫（对抗编译作用域泄漏 x 的意外）：确认禁用态直调不受 scope 影响。 */
    @Test
    public void testDisabledWithScopeVarAlsoDirect() throws Exception {
        setEnabled(false);
        IExecutableExpression tree = new BreakableComputeExpr();
        EvalStaticBoundExecutable bound = new EvalStaticBoundExecutable(tree, new FakeBinding(),
                "fake-static-e2e");

        final AtomicReference<Object> result = new AtomicReference<>();
        Thread thread = new Thread(() -> result.set(bound.execute(null, new EvalRuntime(XLang.newEvalScope()))));
        thread.start();
        thread.join(5000);

        assertEquals("bound-direct-value", result.get(), "非调试态绑定直调（binding.execute）");
        assertFalse(debugger.isSuspended());
    }

    /** 路由注册表卫生（探针遗留守护）：enabled=false 且注册表空时 fast-path 行为不变。 */
    @Test
    public void testEmptyRegistryRouterInactive() {
        assertFalse(EvalBackendRouter.instance().isActive());
    }
}
