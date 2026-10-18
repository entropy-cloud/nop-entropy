/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy/nop-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.backend;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.IConfigReference;
import io.nop.core.lang.eval.DefaultExpressionExecutor;
import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.xlang.XLangConfigs;
import io.nop.xlang.api.ExprEvalAction;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static io.nop.xlang.XLangConfigs.CFG_XLANG_DEBUGGER_ENABLED;
import static io.nop.xlang.XLangConfigs.CFG_XLANG_EXECUTION_FORCE_INTERPRETER;
import static io.nop.xlang.backend.BackendTestFakes.FAKE_STATIC_ID;
import static io.nop.xlang.backend.BackendTestFakes.FakeStaticBackend;
import static io.nop.xlang.backend.BackendTestFakes.FixedValueBinding;
import static io.nop.xlang.backend.EvalBackendDecision.RouteKind;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 调试模式与执行后端联动测试（D4 §3.2/§3.4，设计 xlang-web-debugger 04）：
 * 调试态三落点降级（decide 短路 / executeBoundUnit / executeAdjudicated INTERPRETER 出口）+
 * enabled=false 绑定直通回归 + 观测面契约（reason=debugger-enabled 静默）。
 */
public class TestDebuggerBackendLink {
    private static final String PATH = "/static/dbg-link.xpl";

    private EvalBackendRouter router;
    private FakeStaticBackend staticBackend;
    private RecordingExecutor recordingExecutor;

    static class RecordingExecutor implements IExpressionExecutor {
        final AtomicInteger hits = new AtomicInteger();
        IExecutableExpression lastExpr;

        @Override
        public Object execute(IExecutableExpression expr, EvalRuntime rt) {
            hits.incrementAndGet();
            lastExpr = expr;
            return "via-global-executor";
        }
    }

    @BeforeEach
    public void setUp() {
        router = EvalBackendRouter.instance();
        clearRegistry();
        router.clearRecentDecisions();
        staticBackend = new FakeStaticBackend(Set.of(PATH));
        recordingExecutor = new RecordingExecutor();
    }

    @AfterEach
    public void tearDown() {
        EvalExprProvider.registerGlobalExecutor(DefaultExpressionExecutor.INSTANCE);
        restoreConfig(CFG_XLANG_DEBUGGER_ENABLED);
        restoreConfig(CFG_XLANG_EXECUTION_FORCE_INTERPRETER);
        clearRegistry();
        router.clearRecentDecisions();
    }

    private void clearRegistry() {
        EvalBackendRegistry registry = EvalBackendRegistry.instance();
        for (String id : Set.copyOf(registry.getBackendIds()))
            registry.unregister(registry.getBackend(id));
    }

    private static final java.util.Map<IConfigReference<?>, Object> CONFIG_BASELINE = new java.util.HashMap<>();

    private static void setConfig(IConfigReference<Boolean> ref, boolean value) {
        CONFIG_BASELINE.putIfAbsent(ref, ref.get());
        AppConfig.getConfigProvider().updateConfigValue(ref, value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void restoreConfig(IConfigReference<Boolean> ref) {
        Object base = CONFIG_BASELINE.get(ref);
        if (base != null)
            AppConfig.getConfigProvider().updateConfigValue((IConfigReference) ref, base);
    }

    private final java.util.List<IExecutableExpression> literalTreeRef = new java.util.ArrayList<>();

    private IExecutableExpression literalTree(Object value) {
        IExecutableExpression tree = io.nop.xlang.exec.LiteralExecutable.build(
                io.nop.api.core.util.SourceLocation.fromPath(PATH), value);
        literalTreeRef.clear();
        literalTreeRef.add(tree);
        return tree;
    }

    /** 调试态：executeBoundUnit 降级经全局执行器执行 sourceTree（非 binding）。 */
    @Test
    public void testDebugModeBoundUnitDegradesToGlobalExecutor() {
        setConfig(CFG_XLANG_DEBUGGER_ENABLED, true);
        registryRegister();
        EvalExprProvider.registerGlobalExecutor(recordingExecutor);
        EvalStaticBoundExecutable bound = new EvalStaticBoundExecutable(
                literalTree("tree-value"), new FixedValueBinding("bound-value", "artifact"), FAKE_STATIC_ID);

        Object result = router.executeAdjudicated(bound, new EvalRuntime(XLang.newEvalScope()));

        assertEquals("via-global-executor", result, "降级执行经全局执行器（recording 返回值）——非 binding 直调");
        assertEquals(1, recordingExecutor.hits.get(), "必须经全局执行器（断点可达）");
        assertSame(literalTreeRef.get(0), recordingExecutor.lastExpr, "全局执行器收到的必须是 sourceTree");
        // 决策环：interpreter 决策 reason=debugger-enabled（非 boundUnit、非降级观测）
        EvalBackendDecision last = router.getRecentDecisions().get(router.getRecentDecisions().size() - 1);
        assertEquals(RouteKind.INTERPRETER, last.getKind());
        assertEquals("debugger-enabled", last.getReason());
        assertFalse(last.isDegraded());
    }

    /** enabled=false：绑定直通（binding.execute），全局执行器不被调。 */
    @Test
    public void testDisabledBindingDirectCallUnchanged() {
        // CFG_XLANG_DEBUGGER_ENABLED 默认 false，显式声明断言前提
        setConfig(CFG_XLANG_DEBUGGER_ENABLED, false);
        registryRegister();
        EvalStaticBoundExecutable bound = new EvalStaticBoundExecutable(
                literalTree("tree-value"), new FixedValueBinding("bound-value", "artifact"), FAKE_STATIC_ID);

        Object result = router.executeAdjudicated(bound, new EvalRuntime(XLang.newEvalScope()));

        assertEquals("bound-value", result, "非调试态绑定直调（逐字节现状）");
        assertEquals(0, recordingExecutor.hits.get(), "全局执行器不得被调");
    }

    /** enabled=false 且 router inactive（注册表空）：fast-path 行为不变（现状默认态）。 */
    @Test
    public void testDisabledEmptyRegistryFastPathUnchanged() {
        assertTrue(EvalBackendRegistry.instance().isEmpty());
        assertFalse(router.isActive());
        ExprEvalAction action = XLang.newCompileTool()
                .compileSimpleExpr(io.nop.api.core.util.SourceLocation.fromPath("t:/dbg-link.expr"), "1 + 2");
        Object result = action.invoke(EvalExprProvider.newEvalScope());
        assertEquals(3, result);
        assertTrue(router.getRecentDecisions().isEmpty());
    }

    /** 调试短路：reason=debugger-enabled、静默（不记降级观测）、先于 force-interpreter。 */
    @Test
    public void testDebugShortCircuitReasonAndSilent() {
        setConfig(CFG_XLANG_DEBUGGER_ENABLED, true);
        registryRegister();
        double before = EvalBackendObservation.degradationCount(FAKE_STATIC_ID, "config-disabled");

        EvalBackendDecision decision = router.decide(PATH, literalTree("v"));

        assertEquals(RouteKind.INTERPRETER, decision.getKind());
        assertEquals("debugger-enabled", decision.getReason());
        assertFalse(decision.isDegraded());
        assertEquals(0.0, EvalBackendObservation.degradationCount(FAKE_STATIC_ID, "config-disabled") - before,
                "调试短路必须静默（不记降级观测）");
    }

    /** force-interpreter 同开时：插入顺序决定 reason 取 debugger-enabled（先于 force 短路）。 */
    @Test
    public void testDebugShortCircuitPrecedesForceInterpreter() {
        setConfig(CFG_XLANG_DEBUGGER_ENABLED, true);
        setConfig(CFG_XLANG_EXECUTION_FORCE_INTERPRETER, true);

        EvalBackendDecision decision = router.decide(PATH, literalTree("v"));

        assertEquals(RouteKind.INTERPRETER, decision.getKind());
        assertEquals("debugger-enabled", decision.getReason());
    }

    private void registryRegister() {
        staticBackend.putBinding(PATH, new FixedValueBinding("bound-value", "artifact"));
        EvalBackendRegistry.instance().register(staticBackend);
    }

    private static void assertFalse(boolean condition) {
        org.junit.jupiter.api.Assertions.assertFalse(condition);
    }
}
