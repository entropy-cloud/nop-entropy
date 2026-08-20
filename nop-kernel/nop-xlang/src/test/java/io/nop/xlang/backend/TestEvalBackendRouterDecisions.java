package io.nop.xlang.backend;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.IConfigReference;
import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.api.ExprEvalAction;
import io.nop.xlang.api.XLang;
import io.nop.xlang.exec.LiteralExecutable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static io.nop.xlang.XLangConfigs.CFG_XLANG_EXECUTION_DEPLOYMENT_FORM;
import static io.nop.xlang.XLangConfigs.CFG_XLANG_EXECUTION_FORCE_INTERPRETER;
import static io.nop.xlang.XLangConfigs.CFG_XLANG_EXECUTION_JAVA_BACKEND_ENABLED;
import static io.nop.xlang.XLangConfigs.CFG_XLANG_EXECUTION_TRUFFLE_BACKEND_ENABLED;
import static io.nop.xlang.backend.BackendTestFakes.FAKE_DYNAMIC_ID;
import static io.nop.xlang.backend.BackendTestFakes.FAKE_STATIC_ID;
import static io.nop.xlang.backend.BackendTestFakes.FakeDynamicBackend;
import static io.nop.xlang.backend.BackendTestFakes.FakeStaticBackend;
import static io.nop.xlang.backend.BackendTestFakes.FixedValueBinding;
import static io.nop.xlang.backend.EvalBackendDecision.RouteKind;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 统一决策树逐分支测试（fake 后端注入）：静态命中/未命中、动态可用/降级、第三分支、
 * 单跳无跨跳、强制解释器短路、配置开关矩阵、部署形态钩子、后端未注册回归护栏、
 * 真实出口→choke point 接线（Phase 1 §6 落点 a）。
 */
public class TestEvalBackendRouterDecisions {

    private EvalBackendRouter router;

    @BeforeEach
    public void setUp() {
        router = EvalBackendRouter.instance();
        clearRegistry();
        router.clearRecentDecisions();
    }

    @AfterEach
    public void tearDown() {
        restoreConfig(CFG_XLANG_EXECUTION_FORCE_INTERPRETER);
        restoreConfig(CFG_XLANG_EXECUTION_JAVA_BACKEND_ENABLED);
        restoreConfig(CFG_XLANG_EXECUTION_TRUFFLE_BACKEND_ENABLED);
        restoreDeploymentForm();
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

    private static void setDeploymentForm(String form) {
        CONFIG_BASELINE.putIfAbsent(CFG_XLANG_EXECUTION_DEPLOYMENT_FORM,
                CFG_XLANG_EXECUTION_DEPLOYMENT_FORM.get());
        AppConfig.getConfigProvider().updateConfigValue(CFG_XLANG_EXECUTION_DEPLOYMENT_FORM, form);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void restoreDeploymentForm() {
        Object base = CONFIG_BASELINE.get(CFG_XLANG_EXECUTION_DEPLOYMENT_FORM);
        if (base != null)
            AppConfig.getConfigProvider().updateConfigValue(
                    (IConfigReference) CFG_XLANG_EXECUTION_DEPLOYMENT_FORM, base);
    }

    private static double degradationDelta(String backendId, String reason, Runnable action) {
        double before = EvalBackendObservation.degradationCount(backendId, reason);
        action.run();
        return EvalBackendObservation.degradationCount(backendId, reason) - before;
    }

    private static IExecutableExpression literalTree(String path, Object value) {
        return LiteralExecutable.build(path == null ? null : io.nop.api.core.util.SourceLocation.fromPath(path), value);
    }

    // ---- 后端未注册回归护栏 ----

    @Test
    public void testEmptyRegistryInactiveAndBehaviorUnchanged() {
        assertTrue(EvalBackendRegistry.instance().isEmpty());
        assertFalse(router.isActive());

        // 出口行为与现状一致：真实出口经 XLang.execute 直通全局执行器（解释器求值）
        ExprEvalAction action = XLang.newCompileTool()
                .compileSimpleExpr(io.nop.api.core.util.SourceLocation.fromPath("t:/router/guard.expr"), "1 + 2");
        Object result = action.invoke(EvalExprProvider.newEvalScope());
        assertEquals(3, result);
        // 无裁决记录、无观测
        assertTrue(router.getRecentDecisions().isEmpty());
        assertEquals(0.0, degradationDelta(FAKE_DYNAMIC_ID,
                EvalBackendObservation.REASON_CONFIG_DISABLED, () -> {
                }));
    }

    // ---- 强制解释器诊断模式：全路由短路 + 静默 ----

    @Test
    public void testForceInterpreterShortCircuitSilent() {
        FakeStaticBackend staticBackend = new FakeStaticBackend(Set.of("/static/a.xpl"));
        staticBackend.putBinding("/static/a.xpl", new FixedValueBinding("bound", "artifact"));
        FakeDynamicBackend dynamicBackend = new FakeDynamicBackend();
        register(staticBackend, dynamicBackend);
        setConfig(CFG_XLANG_EXECUTION_FORCE_INTERPRETER, true);

        double delta = degradationDelta(FAKE_STATIC_ID, EvalBackendObservation.REASON_CONFIG_DISABLED, () -> {
            EvalBackendDecision decision = router.decide("/static/a.xpl", literalTree("/static/a.xpl", 1));
            assertEquals(RouteKind.INTERPRETER, decision.getKind());
            assertEquals("force-interpreter", decision.getReason());
            assertFalse(decision.isDegraded());
        });
        assertEquals(0.0, delta);

        double deltaDyn = degradationDelta(FAKE_DYNAMIC_ID, EvalBackendObservation.REASON_CONFIG_DISABLED, () -> {
            EvalBackendDecision decision = router.decide("/dynamic/whatever.expr", null);
            assertEquals(RouteKind.INTERPRETER, decision.getKind());
            assertEquals("force-interpreter", decision.getReason());
        });
        assertEquals(0.0, deltaDyn);
        assertEquals(0, dynamicBackend.getExecutionCount());
    }

    // ---- 静态路径逐分支 ----

    @Test
    public void testStaticHitRoutesToBinding() {
        FakeStaticBackend staticBackend = new FakeStaticBackend(Set.of("/static/a.xpl"));
        staticBackend.putBinding("/static/a.xpl", new FixedValueBinding("bound-value", "gen-artifact"));
        register(staticBackend, null);

        EvalBackendDecision decision = router.decide("/static/a.xpl", literalTree("/static/a.xpl", 1));
        assertEquals(RouteKind.STATIC, decision.getKind());
        assertEquals(FAKE_STATIC_ID, decision.getBackendId());
        assertFalse(decision.isDegraded());
        assertSame("gen-artifact", decision.getStaticBinding().getBindingArtifact());

        // 执行走生成类绑定执行体
        Object result = router.executeAdjudicated(literalTree("/static/a.xpl", 1),
                new EvalRuntime(EvalExprProvider.newEvalScope()));
        assertEquals("bound-value", result);
    }

    @Test
    public void testStaticConfigDisabledDegradesWithObservation() {
        FakeStaticBackend staticBackend = new FakeStaticBackend(Set.of("/static/a.xpl"));
        staticBackend.putBinding("/static/a.xpl", new FixedValueBinding("bound", "artifact"));
        register(staticBackend, null);
        setConfig(CFG_XLANG_EXECUTION_JAVA_BACKEND_ENABLED, false);

        double delta = degradationDelta(FAKE_STATIC_ID, EvalBackendObservation.REASON_CONFIG_DISABLED, () -> {
            EvalBackendDecision decision = router.decide("/static/a.xpl", literalTree("/static/a.xpl", 1));
            assertEquals(RouteKind.INTERPRETER, decision.getKind());
            assertTrue(decision.isDegraded());
            assertEquals(EvalBackendObservation.REASON_CONFIG_DISABLED, decision.getReason());
        });
        assertEquals(1.0, delta, 1e-9);
    }

    @Test
    public void testStaticUnavailableDegradesWithObservation() {
        FakeStaticBackend staticBackend = new FakeStaticBackend(Set.of("/static/a.xpl"));
        staticBackend.markUnavailable("engine-init-failed: injected");
        register(staticBackend, null);

        double delta = degradationDelta(FAKE_STATIC_ID, EvalBackendObservation.REASON_UNAVAILABLE, () -> {
            EvalBackendDecision decision = router.decide("/static/a.xpl", literalTree("/static/a.xpl", 1));
            assertEquals(RouteKind.INTERPRETER, decision.getKind());
            assertTrue(decision.isDegraded());
            assertTrue(decision.getReason().contains("engine-init-failed"));
        });
        assertEquals(1.0, delta, 1e-9);
    }

    @Test
    public void testStaticBindingMissingDegradesWithObservation() {
        FakeStaticBackend staticBackend = new FakeStaticBackend(Set.of("/static/a.xpl"));
        register(staticBackend, null);

        double delta = degradationDelta(FAKE_STATIC_ID, EvalBackendObservation.REASON_GENERATED_BINDING_MISSING, () -> {
            EvalBackendDecision decision = router.decide("/static/a.xpl", literalTree("/static/a.xpl", 1));
            assertEquals(RouteKind.INTERPRETER, decision.getKind());
            assertTrue(decision.isDegraded());
            assertEquals(EvalBackendObservation.REASON_GENERATED_BINDING_MISSING, decision.getReason());
        });
        assertEquals(1.0, delta, 1e-9);
    }

    @Test
    public void testStaticNotInScanListGoesDynamic() {
        FakeStaticBackend staticBackend = new FakeStaticBackend(Set.of("/static/a.xpl"));
        FakeDynamicBackend dynamicBackend = new FakeDynamicBackend();
        register(staticBackend, dynamicBackend);

        // 清单外资源（含 RCM 加载的清单外资源）不适用 java 绑定，走动态路径
        EvalBackendDecision decision = router.decide("/other/b.expr", null);
        assertEquals(RouteKind.DYNAMIC, decision.getKind());
        assertEquals(FAKE_DYNAMIC_ID, decision.getBackendId());
        assertFalse(decision.isDegraded());
    }

    // ---- 单跳降级：无跨跳 ----

    @Test
    public void testStaticDegradationNeverCrossHopsToDynamic() {
        // 静态命中 + java 不可用 + truffle 可用且启用：裁决必须是解释器（单跳 java→interpreter），
        // 不得跨跳到动态后端
        FakeStaticBackend staticBackend = new FakeStaticBackend(Set.of("/static/a.xpl"));
        staticBackend.markUnavailable("engine-init-failed: injected");
        FakeDynamicBackend dynamicBackend = new FakeDynamicBackend();
        register(staticBackend, dynamicBackend);

        EvalBackendDecision decision = router.decide("/static/a.xpl", literalTree("/static/a.xpl", 1));
        assertEquals(RouteKind.INTERPRETER, decision.getKind());
        assertEquals(0, dynamicBackend.getExecutionCount());
    }

    // ---- 动态路径逐分支 ----

    @Test
    public void testDynamicAvailableRoutesToDynamic() {
        FakeDynamicBackend dynamicBackend = new FakeDynamicBackend();
        register(null, dynamicBackend);

        EvalBackendDecision decision = router.decide("/dyn/x.expr", null);
        assertEquals(RouteKind.DYNAMIC, decision.getKind());
        assertFalse(decision.isDegraded());

        Object result = router.executeAdjudicated(literalTree(null, 1),
                new EvalRuntime(EvalExprProvider.newEvalScope()));
        assertEquals("fake-dynamic-value", result);
        assertEquals(1, dynamicBackend.getExecutionCount());
        assertEquals("fake-artifact", router.getRecentDecisions().get(0).getArtifact());
    }

    @Test
    public void testDynamicConfigDisabledDegradesWithObservation() {
        FakeDynamicBackend dynamicBackend = new FakeDynamicBackend();
        register(null, dynamicBackend);
        setConfig(CFG_XLANG_EXECUTION_TRUFFLE_BACKEND_ENABLED, false);

        double delta = degradationDelta(FAKE_DYNAMIC_ID, EvalBackendObservation.REASON_CONFIG_DISABLED, () -> {
            EvalBackendDecision decision = router.decide("/dyn/x.expr", null);
            assertEquals(RouteKind.INTERPRETER, decision.getKind());
            assertTrue(decision.isDegraded());
        });
        assertEquals(1.0, delta, 1e-9);
        assertEquals(0, dynamicBackend.getExecutionCount());
    }

    @Test
    public void testDynamicUnavailableDegradesWithObservation() {
        FakeDynamicBackend dynamicBackend = new FakeDynamicBackend();
        dynamicBackend.markUnavailable("pool-init-failed: injected");
        register(null, dynamicBackend);

        double delta = degradationDelta(FAKE_DYNAMIC_ID, EvalBackendObservation.REASON_UNAVAILABLE, () -> {
            EvalBackendDecision decision = router.decide("/dyn/x.expr", null);
            assertEquals(RouteKind.INTERPRETER, decision.getKind());
            assertTrue(decision.isDegraded());
            assertTrue(decision.getReason().contains("pool-init-failed"));
        });
        assertEquals(1.0, delta, 1e-9);
    }

    @Test
    public void testDynamicNotRegisteredSilentInterpreter() {
        FakeStaticBackend staticBackend = new FakeStaticBackend(Collections.emptySet());
        register(staticBackend, null);

        double delta = degradationDelta(FAKE_DYNAMIC_ID, EvalBackendObservation.REASON_CONFIG_DISABLED, () -> {
            EvalBackendDecision decision = router.decide("/dyn/x.expr", null);
            assertEquals(RouteKind.INTERPRETER, decision.getKind());
            assertFalse(decision.isDegraded());
            assertEquals("dynamic-backend-not-registered", decision.getReason());
        });
        assertEquals(0.0, delta);
    }

    @Test
    public void testNativeDeploymentFormExcludesDynamicSilently() {
        FakeDynamicBackend dynamicBackend = new FakeDynamicBackend();
        register(null, dynamicBackend);
        setDeploymentForm("native-image");

        double delta = degradationDelta(FAKE_DYNAMIC_ID, EvalBackendObservation.REASON_CONFIG_DISABLED, () -> {
            EvalBackendDecision decision = router.decide("/dyn/x.expr", null);
            assertEquals(RouteKind.INTERPRETER, decision.getKind());
            assertFalse(decision.isDegraded());
            assertEquals("native-deployment", decision.getReason());
        });
        assertEquals(0.0, delta);
        restoreDeploymentForm();
    }

    // ---- 第三分支：单元级翻译失败 → 降级解释器 + 观测 ----

    @Test
    public void testThirdBranchFallbackRoutesToInterpreterWithObservation() {
        FakeDynamicBackend dynamicBackend = new FakeDynamicBackend();
        dynamicBackend.setNextOutcome(EvalBackendDynamicOutcome.fallback(
                EvalBackendDynamicOutcome.FALLBACK_UNIT_TRANSLATION_FAILURE, "event-detail"));
        register(null, dynamicBackend);

        IExecutableExpression tree = literalTree(null, 7);
        double delta = degradationDelta(FAKE_DYNAMIC_ID,
                EvalBackendObservation.REASON_UNIT_TRANSLATION_FAILURE,
                () -> router.executeAdjudicated(tree, new EvalRuntime(EvalExprProvider.newEvalScope())));

        assertEquals(1.0, delta, 1e-9);
        assertEquals(1, dynamicBackend.getExecutionCount());
        EvalBackendDecision decision = router.getRecentDecisions().get(0);
        assertEquals(RouteKind.INTERPRETER, decision.getKind());
        assertTrue(decision.isDegraded());
        assertEquals(EvalBackendObservation.REASON_UNIT_TRANSLATION_FAILURE, decision.getReason());
        // 解释器执行原树
        assertSame(tree, decision.getArtifact());
    }

    // ---- 真实求值错误必须重抛（fail-fast，不静默吞没） ----

    @Test
    public void testDynamicBackendErrorPropagates() {
        FakeDynamicBackend dynamicBackend = new FakeDynamicBackend() {
            @Override
            public EvalBackendDynamicOutcome executeDynamic(EvalBackendDynamicRequest request) {
                super.executeDynamic(request);
                throw new IllegalStateException("genuine evaluation error");
            }
        };
        register(null, dynamicBackend);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> router.executeAdjudicated(literalTree(null, 1),
                        new EvalRuntime(EvalExprProvider.newEvalScope())));
    }

    // ---- 真实出口 → choke point → 裁决入口接线验证 ----

    @Test
    public void testRealExitFlowsThroughAdjudicationChokePoint() {
        FakeDynamicBackend dynamicBackend = new FakeDynamicBackend();
        dynamicBackend.setNextOutcome(EvalBackendDynamicOutcome.ofValue(42, "truffle-like-artifact"));
        register(null, dynamicBackend);
        assertTrue(router.isActive());

        // 动态编译出口（compileSimpleExpr）→ ExprEvalAction.invoke → XLang.execute → 统一裁决
        ExprEvalAction action = XLang.newCompileTool()
                .compileSimpleExpr(io.nop.api.core.util.SourceLocation.fromPath("t:/router/real-exit.expr"), "100 + 1");
        Object result = action.invoke(EvalExprProvider.newEvalScope());

        assertEquals(42, result);
        assertEquals(1, dynamicBackend.getExecutionCount());
        EvalBackendDecision decision = router.getRecentDecisions().get(0);
        assertEquals(RouteKind.DYNAMIC, decision.getKind());
        assertEquals("truffle-like-artifact", decision.getArtifact());
        assertNull(decision.getReason());
    }

    // ---- 配置开关矩阵：三开关 × 两后端可用性 ----

    @Test
    public void testConfigSwitchMatrix() {
        for (boolean javaEnabled : new boolean[]{true, false}) {
            for (boolean truffleEnabled : new boolean[]{true, false}) {
                for (boolean staticAvailable : new boolean[]{true, false}) {
                    for (boolean dynamicAvailable : new boolean[]{true, false}) {
                        try {
                            setConfig(CFG_XLANG_EXECUTION_JAVA_BACKEND_ENABLED, javaEnabled);
                            setConfig(CFG_XLANG_EXECUTION_TRUFFLE_BACKEND_ENABLED, truffleEnabled);

                            FakeStaticBackend staticBackend = new FakeStaticBackend(new HashSet<>(Set.of("/m/x.xpl")));
                            if (!staticAvailable)
                                staticBackend.markUnavailable("matrix");
                            staticBackend.putBinding("/m/x.xpl", new FixedValueBinding("v", "a"));
                            FakeDynamicBackend dynamicBackend = new FakeDynamicBackend();
                            if (!dynamicAvailable)
                                dynamicBackend.markUnavailable("matrix");
                            register(staticBackend, dynamicBackend);

                            RouteKind staticKind = router.decide("/m/x.xpl", literalTree("/m/x.xpl", 1)).getKind();
                            RouteKind dynamicKind = router.decide("/m/y.expr", null).getKind();

                            // 静态分支：java 启用+可用 → STATIC；否则 INTERPRETER（永不跨跳 DYNAMIC）
                            assertEquals(javaEnabled && staticAvailable ? RouteKind.STATIC : RouteKind.INTERPRETER,
                                    staticKind, "javaEnabled=" + javaEnabled + ",staticAvailable=" + staticAvailable);
                            // 动态分支：truffle 启用+可用 → DYNAMIC；否则 INTERPRETER
                            assertEquals(truffleEnabled && dynamicAvailable ? RouteKind.DYNAMIC : RouteKind.INTERPRETER,
                                    dynamicKind, "truffleEnabled=" + truffleEnabled + ",dynamicAvailable=" + dynamicAvailable);
                        } finally {
                            clearRegistry();
                        }
                    }
                }
            }
        }
    }

    private void register(FakeStaticBackend staticBackend, FakeDynamicBackend dynamicBackend) {
        if (staticBackend != null)
            EvalBackendRegistry.instance().register(staticBackend);
        if (dynamicBackend != null)
            EvalBackendRegistry.instance().register(dynamicBackend);
    }
}
