package io.nop.xlang.java.compare;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.IConfigReference;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.api.XplModel;
import io.nop.xlang.backend.EvalBackendDecision;
import io.nop.xlang.backend.EvalBackendObservation;
import io.nop.xlang.backend.EvalBackendRouter;
import io.nop.xlang.backend.EvalStaticBoundExecutable;
import io.nop.xlang.backend.EvalStaticDegradedExecutable;
import io.nop.xlang.java.backend.GeneratedClassBindingBinder;
import io.nop.xlang.java.backend.IEvalStaticBindingBinder;
import io.nop.xlang.java.backend.JavaEvalExecutionBackend;
import io.nop.xlang.java.gen.EvalMethodConvention;
import io.nop.xlang.java.gen.ExecutableTreeFingerprints;
import io.nop.xlang.java.gen.GeneratedClassManifest;
import io.nop.xlang.compare.CompareValues;
import io.nop.xlang.compare.RecordingEvalOutput;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static io.nop.xlang.XLangConfigs.CFG_XLANG_EXECUTION_JAVA_BACKEND_ENABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模型加载期绑定接线与直通语义测试（I10 Phase 2，D3 裁定落地）：
 * 绑定 hook 真实被模型加载路径调用（parseXpl 后执行体身份 = 生成类绑定 artifact）、
 * 已绑定直通（无每次执行指纹重算/无虚假 WARN——计数断言）、降级标记（观测一次、执行期静默）、
 * 清单外不包装、缺省空态 = 行为与现状一致。
 */
public class TestModelLoadingBinding {

    private static final ProductionBindingCorpus.StaticUnit EXPR_UNIT = findUnit("static-a/scope-chain");
    private static final ProductionBindingCorpus.StaticUnit TPL_UNIT = findUnit("static-b/tpl-text");

    private static IConfigReference<Boolean> javaSwitch;

    private static Boolean switchBaseline;

    private static String exprFingerprint;

    private static String tplFingerprint;

    private static ProductionBindingCorpus.StaticUnit findUnit(String pathPart) {
        return ProductionBindingCorpus.staticUnits().stream()
                .filter(u -> u.getPath().contains(pathPart))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("corpus unit not found: " + pathPart));
    }

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
        javaSwitch = CFG_XLANG_EXECUTION_JAVA_BACKEND_ENABLED;
        switchBaseline = javaSwitch.get();
        exprFingerprint = ExecutableTreeFingerprints.fingerprint(
                ProductionBindingCorpus.compileCanonicalTree(EXPR_UNIT));
        tplFingerprint = ExecutableTreeFingerprints.fingerprint(
                ProductionBindingCorpus.compileCanonicalTree(TPL_UNIT));
    }

    @AfterAll
    public static void resetBackend() {
        AppConfig.getConfigProvider().updateConfigValue(javaSwitch, switchBaseline);
        JavaEvalExecutionBackend.instance().setStaticScanList(Collections.emptySet());
        JavaEvalExecutionBackend.instance().setBinder(null);
        JavaEvalExecutionBackend.instance().clearUnavailable();
        EvalBackendRouter.instance().clearRecentDecisions();
    }

    @BeforeEach
    public void setUp() {
        EvalBackendRouter.instance().clearRecentDecisions();
        AppConfig.getConfigProvider().updateConfigValue(javaSwitch, true);
        JavaEvalExecutionBackend.instance().clearUnavailable();
    }

    @AfterEach
    public void tearDown() {
        AppConfig.getConfigProvider().updateConfigValue(javaSwitch, true);
        JavaEvalExecutionBackend.instance().setStaticScanList(Collections.emptySet());
        JavaEvalExecutionBackend.instance().setBinder(null);
        JavaEvalExecutionBackend.instance().clearUnavailable();
        EvalBackendRouter.instance().clearRecentDecisions();
    }

    private static String fixtureFqn(ProductionBindingCorpus.StaticUnit unit) {
        return EvalMethodConvention.GENERATED_PACKAGE + '.' + EvalMethodConvention.generatedClassName(unit.getPath());
    }

    private static GeneratedClassManifest fullManifest() {
        Map<String, GeneratedClassManifest.Entry> entries = new LinkedHashMap<>();
        entries.put(EXPR_UNIT.getPath(),
                new GeneratedClassManifest.Entry(EXPR_UNIT.getPath(), fixtureFqn(EXPR_UNIT), exprFingerprint));
        entries.put(TPL_UNIT.getPath(),
                new GeneratedClassManifest.Entry(TPL_UNIT.getPath(), fixtureFqn(TPL_UNIT), tplFingerprint));
        return GeneratedClassManifest.of(entries);
    }

    // ---- 绑定 hook 接线：模型加载后执行体身份 = 生成类绑定 artifact（非解释器树） ----

    @Test
    public void testLoadedModelCarriesGeneratedBindingArtifact() {
        JavaEvalExecutionBackend.instance().setGeneratedClassManifest(fullManifest());
        JavaEvalExecutionBackend.instance()
                .setStaticScanList(List.of(EXPR_UNIT.getPath(), TPL_UNIT.getPath()));

        XplModel model = ProductionBindingCorpus.loadBound(EXPR_UNIT);
        IExecutableExpression expr = model.getExpr();
        assertTrue(expr instanceof EvalStaticBoundExecutable,
                "loaded model executable must be the load-time bound wrapper");
        Method artifact = (Method) ((EvalStaticBoundExecutable) expr).getBinding().getBindingArtifact();
        assertEquals(fixtureFqn(EXPR_UNIT), artifact.getDeclaringClass().getName());

        // invoke → 已绑定直通：STATIC 裁决 + artifact = 生成类入口 Method
        IEvalScope scope = EvalExprProvider.newEvalScope(Map.of("x", 10));
        Object result = model.invoke(scope);
        Object expected = EvalExprProvider.getGlobalExecutor().execute(
                ProductionBindingCorpus.compileCanonicalTree(EXPR_UNIT),
                new EvalRuntime(EvalExprProvider.newEvalScope(Map.of("x", 10))));
        assertTrue(CompareValues.typedEquals(expected, result));
        EvalBackendDecision decision = EvalBackendRouter.instance().getRecentDecisions().get(0);
        assertEquals(EvalBackendDecision.RouteKind.STATIC, decision.getKind());
        assertEquals("java", decision.getBackendId());
        assertFalse(decision.isDegraded());
        assertSame(artifact, decision.getArtifact());
    }

    // ---- 模板单元（$out 双参入口）经加载期绑定执行 ----

    @Test
    public void testTemplateUnitBindsWithOutputParam() {
        JavaEvalExecutionBackend.instance().setGeneratedClassManifest(fullManifest());
        JavaEvalExecutionBackend.instance()
                .setStaticScanList(List.of(EXPR_UNIT.getPath(), TPL_UNIT.getPath()));

        XplModel model = ProductionBindingCorpus.loadBound(TPL_UNIT);
        assertTrue(model.getExpr() instanceof EvalStaticBoundExecutable);

        RecordingEvalOutput out = new RecordingEvalOutput();
        IEvalScope scope = EvalExprProvider.newEvalScope();
        Object result = ((EvalStaticBoundExecutable) model.getExpr())
                .execute(null, new EvalRuntime(scope, out));
        assertNull(result);

        // 输出层 vs 解释器基线（干净树经全局执行器 + 录制缓冲）
        RecordingEvalOutput baselineOut = new RecordingEvalOutput();
        IExecutableExpression clean = ProductionBindingCorpus.compileCanonicalTree(TPL_UNIT);
        EvalExprProvider.getGlobalExecutor().execute(clean,
                new EvalRuntime(EvalExprProvider.newEvalScope(), baselineOut));
        assertTrue(CompareValues.typedEquals(baselineOut.getCalls(), out.getCalls()),
                "output calls diverged: baseline=" + baselineOut.getCalls() + " bound=" + out.getCalls());
    }

    // ---- 已绑定直通：多次 invoke 无重复 binder 咨询/指纹计算、无虚假观测（计数断言） ----

    @Test
    public void testBoundPassThroughSkipsRepetitiveConsultation() {
        AtomicInteger consults = new AtomicInteger();
        GeneratedClassManifest manifest = fullManifest();
        IEvalStaticBindingBinder production = new GeneratedClassBindingBinder(manifest);
        JavaEvalExecutionBackend.instance().setBinder((resourcePath, tree) -> {
            consults.incrementAndGet();
            return production.findStaticBinding(resourcePath, tree);
        });
        JavaEvalExecutionBackend.instance()
                .setStaticScanList(List.of(EXPR_UNIT.getPath(), TPL_UNIT.getPath()));

        XplModel model = ProductionBindingCorpus.loadBound(EXPR_UNIT);
        assertEquals(1, consults.get(), "binder consulted exactly once at load time");

        Object expected = EvalExprProvider.getGlobalExecutor().execute(
                ProductionBindingCorpus.compileCanonicalTree(EXPR_UNIT),
                new EvalRuntime(EvalExprProvider.newEvalScope(Map.of("x", 10))));
        double degradedBefore = EvalBackendObservation.degradationCount("java",
                EvalBackendObservation.REASON_GENERATED_BINDING_MISSING);
        for (int i = 0; i < 3; i++) {
            assertTrue(CompareValues.typedEquals(expected,
                    model.invoke(EvalExprProvider.newEvalScope(Map.of("x", 10)))));
        }
        assertEquals(1, consults.get(), "no repeated binder consultation/fingerprinting per execution");
        assertEquals(0.0, EvalBackendObservation.degradationCount("java",
                EvalBackendObservation.REASON_GENERATED_BINDING_MISSING) - degradedBefore, 1e-9,
                "no spurious degradation observation for bound executions");
    }

    // ---- 降级标记：指纹失配 → 绑定期观测一次 + 执行期静默解释器 ----

    @Test
    public void testFingerprintMismatchDegradesAtLoadAndExecutesSilently() {
        Map<String, GeneratedClassManifest.Entry> entries = new LinkedHashMap<>();
        entries.put(EXPR_UNIT.getPath(), new GeneratedClassManifest.Entry(EXPR_UNIT.getPath(),
                fixtureFqn(EXPR_UNIT), "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"));
        JavaEvalExecutionBackend.instance().setGeneratedClassManifest(GeneratedClassManifest.of(entries));
        JavaEvalExecutionBackend.instance().setStaticScanList(List.of(EXPR_UNIT.getPath()));

        double before = EvalBackendObservation.degradationCount("java",
                EvalBackendObservation.REASON_GENERATED_FINGERPRINT_MISMATCH);
        XplModel model = ProductionBindingCorpus.loadBound(EXPR_UNIT);
        assertTrue(model.getExpr() instanceof EvalStaticDegradedExecutable);
        assertEquals(1.0, EvalBackendObservation.degradationCount("java",
                EvalBackendObservation.REASON_GENERATED_FINGERPRINT_MISMATCH) - before, 1e-9);

        IExecutableExpression clean = ProductionBindingCorpus.compileCanonicalTree(EXPR_UNIT);
        Object baseline = EvalExprProvider.getGlobalExecutor().execute(clean,
                new EvalRuntime(EvalExprProvider.newEvalScope(Map.of("x", 10))));
        Object degradedResult = model.invoke(EvalExprProvider.newEvalScope(Map.of("x", 10)));
        assertTrue(CompareValues.typedEquals(baseline, degradedResult));

        // 执行期静默：观测不再增长；裁决环 INTERPRETER + artifact = 原树
        assertEquals(1.0, EvalBackendObservation.degradationCount("java",
                EvalBackendObservation.REASON_GENERATED_FINGERPRINT_MISMATCH) - before, 1e-9);
        EvalBackendDecision decision = EvalBackendRouter.instance().getRecentDecisions().get(0);
        assertEquals(EvalBackendDecision.RouteKind.INTERPRETER, decision.getKind());
        assertTrue(decision.isDegraded());
        assertSame(((EvalStaticDegradedExecutable) model.getExpr()).getSourceTree(), decision.getArtifact());
    }

    // ---- 清单外：不包装、动态路径、零降级观测 ----

    @Test
    public void testOutOfListUnitNotWrappedAndNoObservation() {
        JavaEvalExecutionBackend.instance().setGeneratedClassManifest(fullManifest());
        JavaEvalExecutionBackend.instance().setStaticScanList(List.of(EXPR_UNIT.getPath()));

        double beforeMissing = EvalBackendObservation.degradationCount("java",
                EvalBackendObservation.REASON_GENERATED_BINDING_MISSING);
        XplModel model = ProductionBindingCorpus.loadBound(TPL_UNIT);
        assertFalse(model.getExpr() instanceof EvalStaticBoundExecutable);
        assertFalse(model.getExpr() instanceof EvalStaticDegradedExecutable);
        assertEquals(0.0, EvalBackendObservation.degradationCount("java",
                EvalBackendObservation.REASON_GENERATED_BINDING_MISSING) - beforeMissing, 1e-9);
    }

    // ---- 缺省空态：无供给 = 行为与现状一致（干净树 + 正确结果） ----

    @Test
    public void testDefaultEmptyStateMatchesCurrentBehavior() {
        JavaEvalExecutionBackend.instance().setStaticScanList(Collections.emptySet());
        JavaEvalExecutionBackend.instance().setBinder(null);

        XplModel model = ProductionBindingCorpus.loadBound(EXPR_UNIT);
        assertFalse(model.getExpr() instanceof EvalStaticBoundExecutable);
        assertFalse(model.getExpr() instanceof EvalStaticDegradedExecutable);
        Object expected = EvalExprProvider.getGlobalExecutor().execute(
                ProductionBindingCorpus.compileCanonicalTree(EXPR_UNIT),
                new EvalRuntime(EvalExprProvider.newEvalScope(Map.of("x", 10))));
        assertTrue(CompareValues.typedEquals(expected,
                model.invoke(EvalExprProvider.newEvalScope(Map.of("x", 10)))));
    }
}
