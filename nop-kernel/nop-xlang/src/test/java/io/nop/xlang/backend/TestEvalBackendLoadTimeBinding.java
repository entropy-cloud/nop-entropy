package io.nop.xlang.backend;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.resource.impl.InMemoryTextResource;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XplModel;
import io.nop.xlang.ast.XLangOutputMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;

import static io.nop.xlang.XLangConfigs.CFG_XLANG_EXECUTION_FORCE_INTERPRETER;
import static io.nop.xlang.XLangConfigs.CFG_XLANG_EXECUTION_JAVA_BACKEND_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模型加载期绑定助手（{@code bindLoadedUnit}）与已绑定直通语义的 nop-xlang 侧测试
 * （I10 Phase 2，D3 裁定；fake 静态后端承载——真实生产 binder 在 nop-xlang-java 测试域覆盖）：
 * §五伪代码逐分支（未启用/清单外/不可用/命中/缺失）+ choke point 对已裁定执行体的直通。
 */
public class TestEvalBackendLoadTimeBinding {

    private static final String PATH = "/load-time/fake-unit.xpl";

    private static final String SOURCE = "<c:script>1 + 2</c:script>";

    private EvalBackendRouter router;

    private BackendTestFakes.FakeStaticBackend backend;

    private boolean registered;

    @BeforeEach
    public void setUp() {
        router = EvalBackendRouter.instance();
        clearRegistry();
        router.clearRecentDecisions();
        restoreDefaults();
        backend = new BackendTestFakes.FakeStaticBackend(Set.of(PATH));
        EvalBackendRegistry.instance().register(backend);
        registered = true;
    }

    @AfterEach
    public void tearDown() {
        if (registered)
            EvalBackendRegistry.instance().unregister(backend);
        clearRegistry();
        router.clearRecentDecisions();
        restoreDefaults();
    }

    private void clearRegistry() {
        EvalBackendRegistry registry = EvalBackendRegistry.instance();
        for (String id : Set.copyOf(registry.getBackendIds()))
            registry.unregister(registry.getBackend(id));
    }

    private void restoreDefaults() {
        AppConfig.getConfigProvider().updateConfigValue(CFG_XLANG_EXECUTION_FORCE_INTERPRETER, false);
        AppConfig.getConfigProvider().updateConfigValue(CFG_XLANG_EXECUTION_JAVA_BACKEND_ENABLED, true);
    }

    private IExecutableExpression plainTree() {
        XplModel model = XLang.parseXpl(new InMemoryTextResource(PATH, SOURCE), XLangOutputMode.none);
        return model.getExpr();
    }

    // ---- §五条件1：force-interpreter / 开关关 → 返回原树（静默） ----

    @Test
    public void testForceInterpreterReturnsPlainTree() {
        AppConfig.getConfigProvider().updateConfigValue(CFG_XLANG_EXECUTION_FORCE_INTERPRETER, true);
        try {
            IExecutableExpression tree = plainTree();
            IExecutableExpression bound = router.bindLoadedUnit(PATH, tree);
            assertSame(tree, bound);
        } finally {
            AppConfig.getConfigProvider().updateConfigValue(CFG_XLANG_EXECUTION_FORCE_INTERPRETER, false);
        }
    }

    @Test
    public void testDisabledSwitchReturnsPlainTree() {
        AppConfig.getConfigProvider().updateConfigValue(CFG_XLANG_EXECUTION_JAVA_BACKEND_ENABLED, false);
        try {
            IExecutableExpression tree = plainTree();
            assertSame(tree, router.bindLoadedUnit(PATH, tree));
        } finally {
            AppConfig.getConfigProvider().updateConfigValue(CFG_XLANG_EXECUTION_JAVA_BACKEND_ENABLED, true);
        }
    }

    // ---- §五条件2：清单外 → 返回原树（动态路径，不记降级） ----

    @Test
    public void testOutOfListReturnsPlainTree() {
        IExecutableExpression tree = plainTree();
        assertSame(tree, router.bindLoadedUnit("/other/not-listed.xpl", tree));
    }

    // ---- 注册表空 → 返回原树（现状行为） ----

    @Test
    public void testEmptyRegistryReturnsPlainTree() {
        clearRegistry();
        IExecutableExpression tree = plainTree();
        assertSame(tree, router.bindLoadedUnit(PATH, tree));
        // parseXpl 亦不包装
        XplModel model = XLang.parseXpl(new InMemoryTextResource(PATH, SOURCE), XLangOutputMode.none);
        assertFalse(model.getExpr() instanceof EvalStaticBoundExecutable);
        assertFalse(model.getExpr() instanceof EvalStaticDegradedExecutable);
    }

    // ---- 不可用条目：绑定期观测（unavailable）+ 降级标记；执行期静默解释器 ----

    @Test
    public void testUnavailableBackendObservesAtLoadAndExecutesSilently() {
        backend.markUnavailable("pipeline-missed");
        double before = EvalBackendObservation.degradationCount(BackendTestFakes.FAKE_STATIC_ID,
                "unavailable:pipeline-missed");

        XplModel model = XLang.parseXpl(new InMemoryTextResource(PATH, SOURCE), XLangOutputMode.none);
        assertTrue(model.getExpr() instanceof EvalStaticDegradedExecutable);
        assertEquals(1.0, EvalBackendObservation.degradationCount(BackendTestFakes.FAKE_STATIC_ID,
                "unavailable:pipeline-missed") - before, 1e-9);

        IEvalScope scope = EvalExprProvider.newEvalScope();
        Object result = model.invoke(scope);
        assertEquals(3, result, "degraded wrapper executes interpreter fallback");

        EvalBackendDecision decision = router.getRecentDecisions().get(0);
        assertEquals(EvalBackendDecision.RouteKind.INTERPRETER, decision.getKind());
        assertTrue(decision.isDegraded());
        // 执行期不重复观测
        assertEquals(1.0, EvalBackendObservation.degradationCount(BackendTestFakes.FAKE_STATIC_ID,
                "unavailable:pipeline-missed") - before, 1e-9);
    }

    // ---- 绑定命中：bound 包装 + choke point 直通（执行绑定体、artifact = 绑定证据） ----

    @Test
    public void testBoundUnitPassesThroughChokePoint() {
        Object artifact = new Object();
        backend.putBinding(PATH, new IEvalStaticBinding() {
            @Override
            public Object execute(EvalRuntime rt) {
                return "generated-execution";
            }

            @Override
            public Object getBindingArtifact() {
                return artifact;
            }
        });

        XplModel model = XLang.parseXpl(new InMemoryTextResource(PATH, SOURCE), XLangOutputMode.none);
        assertTrue(model.getExpr() instanceof EvalStaticBoundExecutable);

        Object result = model.invoke(EvalExprProvider.newEvalScope());
        assertEquals("generated-execution", result);

        EvalBackendDecision decision = router.getRecentDecisions().get(0);
        assertEquals(EvalBackendDecision.RouteKind.STATIC, decision.getKind());
        assertEquals(BackendTestFakes.FAKE_STATIC_ID, decision.getBackendId());
        assertFalse(decision.isDegraded());
        assertSame(artifact, decision.getArtifact());
    }

    // ---- 绑定缺失（binder null）：降级标记；执行期直通解释器且不再补记观测（防双记裁定） ----

    @Test
    public void testMissingBindingDegradesWithoutRouterObservation() {
        double missingBefore = EvalBackendObservation.degradationCount(BackendTestFakes.FAKE_STATIC_ID,
                EvalBackendObservation.REASON_GENERATED_BINDING_MISSING);

        XplModel model = XLang.parseXpl(new InMemoryTextResource(PATH, SOURCE), XLangOutputMode.none);
        assertTrue(model.getExpr() instanceof EvalStaticDegradedExecutable,
                "null binding (应有而缺失) wraps degraded marker");

        Object result = model.invoke(EvalExprProvider.newEvalScope());
        assertEquals(3, result);

        // 直通解释器：裁决 INTERPRETER + 原树 artifact；观测由供给方（生产 binder）自记，
        // 加载期助手/执行期直通均不补记（D5 观测记录方裁定）
        EvalBackendDecision decision = router.getRecentDecisions().get(0);
        assertEquals(EvalBackendDecision.RouteKind.INTERPRETER, decision.getKind());
        assertTrue(decision.isDegraded());
        assertSame(((EvalStaticDegradedExecutable) model.getExpr()).getSourceTree(), decision.getArtifact());
        assertEquals(0.0, EvalBackendObservation.degradationCount(BackendTestFakes.FAKE_STATIC_ID,
                EvalBackendObservation.REASON_GENERATED_BINDING_MISSING) - missingBefore, 1e-9);
    }

    // ---- sourceTree 结构委托：location/visit 保持原树（源位置保真） ----

    @Test
    public void testWrapperDelegatesLocation() {
        backend.putBinding(PATH, new IEvalStaticBinding() {
            @Override
            public Object execute(EvalRuntime rt) {
                return null;
            }

            @Override
            public Object getBindingArtifact() {
                return "artifact";
            }
        });
        XplModel model = XLang.parseXpl(new InMemoryTextResource(PATH, SOURCE), XLangOutputMode.none);
        IExecutableExpression wrapped = model.getExpr();
        SourceLocation loc = wrapped.getLocation();
        assertEquals(PATH, loc.getPath());
    }
}
