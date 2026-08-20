package io.nop.xlang.java.compare;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.api.ExprEvalAction;
import io.nop.xlang.api.XLang;
import io.nop.xlang.backend.EvalBackendDecision;
import io.nop.xlang.backend.EvalBackendObservation;
import io.nop.xlang.backend.EvalBackendRouter;
import io.nop.xlang.backend.EvalStaticBoundExecutable;
import io.nop.xlang.backend.EvalStaticDegradedExecutable;
import io.nop.xlang.compare.BackendExecRequest;
import io.nop.xlang.compare.BackendExecutionEvidence;
import io.nop.xlang.compare.BackendExecutionResult;
import io.nop.xlang.compare.CompareBackendIds;
import io.nop.xlang.compare.CompareUnit;
import io.nop.xlang.compare.CompareUnitKind;
import io.nop.xlang.compare.CompareUnitReport;
import io.nop.xlang.compare.CompareValues;
import io.nop.xlang.compare.ExecCompareHarness;
import io.nop.xlang.compare.ICompareUnitCompiler;
import io.nop.xlang.compare.IEvalBackendColumn;
import io.nop.xlang.java.backend.JavaEvalExecutionBackend;
import io.nop.xlang.java.gen.EvalMethodConvention;
import io.nop.xlang.java.gen.ExecutableTreeFingerprints;
import io.nop.xlang.java.gen.GeneratedClassManifest;
import io.nop.xlang.java.gen.GeneratedEvalBinding;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 绑定路由对拍验收（I10 Phase 3，roadmap I10 三项验收）——经**生产绑定路径**：
 * 可注入双清单供给（合成清单 + 夹具生成类）→ 模型加载（XLang.parseXpl 绑定 hook）→ 绑定 →
 * invoke（choke point 已绑定直通）；非 {@code JavaBackendColumn} 直驱、非 nop-javac 内存编译通路、
 * 无自定义 ClassLoader（D6 四约束）。三层断言（返回值 typedEquals / scope 副作用 / 输出缓冲 /
 * 异常语义（错误码 + SourceLocation 回映射））+ 身份断言（执行体 = 生成类绑定 artifact）经
 * I1 harness 断言工具承载（ExecCompareHarness + CompareValues/SideEffectSnapshot/RecordingEvalOutput
 * + JavaBackendIdentityRule——显式引用关系）。
 */
public class TestProductionBindingRoutingScenarios {

    /** corpus 静态单元（重映射到 VFS 标准路径口径的 harness 单元） */
    private static List<CompareUnit> harnessUnits;

    private static ExecCompareHarness harness;

    private static List<String> allPaths;

    // ---- 验收第一项：绑定路由对拍（java 列 vs 解释器列三层一致 + 身份断言） ----

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();

        // harness：解释器基线列 + 生产绑定路径 java 列（身份规则 = JavaBackendIdentityRule）
        // 双清单供给由各用例自装（用例级隔离：红/绿用例替换全局供给，方法序不可依赖）
        List<ProductionBindingCorpus.StaticUnit> units = ProductionBindingCorpus.staticUnits();
        harnessUnits = new ArrayList<>();
        for (ProductionBindingCorpus.StaticUnit unit : units) {
            harnessUnits.add(new CompareUnit(unit.getUnit().getName(), CompareUnitKind.STATIC,
                    ProductionBindingCorpus.stdPath(unit.getUnit().getSourceLocationPath()),
                    ProductionBindingCorpus.stdPath(unit.getUnit().getSourceLocationPath()),
                    unit.getUnit().getInputVars(),
                    ProductionBindingCorpus.remapExpectation(unit.getUnit().getExpectation()),
                    unit.getUnit().getCategory()));
        }

        harness = ExecCompareHarness.withInterpreterBaseline();
        harness.registerColumn(new ProductionBindingColumn());
        harness.registerIdentityRule(new io.nop.xlang.java.compare.JavaBackendIdentityRule());
        harness.setCompiler(new ICompareUnitCompiler() {
            private final Map<String, ProductionBindingCorpus.StaticUnit> byPath = byPath(units);

            @Override
            public IExecutableExpression compile(CompareUnit unit) {
                // 干净编译（与生产装载同语义、不经绑定 hook）——解释器基线列与期望断言的树
                return ProductionBindingCorpus.compileCanonicalTree(byPath.get(unit.getSourceLocationPath()));
            }
        });
    }

    private static Map<String, ProductionBindingCorpus.StaticUnit> byPath(
            List<ProductionBindingCorpus.StaticUnit> units) {
        Map<String, ProductionBindingCorpus.StaticUnit> map = new LinkedHashMap<>();
        for (ProductionBindingCorpus.StaticUnit unit : units)
            map.put(unit.getPath(), unit);
        return map;
    }

    @AfterAll
    public static void resetBackend() {
        JavaEvalExecutionBackend.instance().setStaticScanList(Collections.emptySet());
        JavaEvalExecutionBackend.instance().setBinder(null);
        JavaEvalExecutionBackend.instance().clearUnavailable();
        EvalBackendRouter.instance().clearRecentDecisions();
    }

    /** 全量双清单供给安装（矩阵/身份用例）：扫描清单 = 全部 corpus 静态单元；清单 = 夹具类 + 树指纹 */
    static void installFullSupplies() {
        List<String> allPaths = new ArrayList<>();
        Map<String, GeneratedClassManifest.Entry> entries = new LinkedHashMap<>();
        for (ProductionBindingCorpus.StaticUnit unit : ProductionBindingCorpus.staticUnits()) {
            allPaths.add(unit.getPath());
            entries.put(unit.getPath(), new GeneratedClassManifest.Entry(unit.getPath(),
                    fixtureFqn(unit.getPath()),
                    ExecutableTreeFingerprints.fingerprint(ProductionBindingCorpus.compileCanonicalTree(unit))));
        }
        JavaEvalExecutionBackend.instance().clearUnavailable();
        JavaEvalExecutionBackend.instance().setGeneratedClassManifest(GeneratedClassManifest.of(entries));
        JavaEvalExecutionBackend.instance().setStaticScanList(allPaths);
    }

    @BeforeEach
    public void setUp() {
        EvalBackendRouter.instance().clearRecentDecisions();
        EvalBackendObservation.clearSteadyStateDedup();
    }

    @AfterEach
    public void tearDown() {
        EvalBackendRouter.instance().clearRecentDecisions();
    }

    static String fixtureFqn(String path) {
        return EvalMethodConvention.GENERATED_PACKAGE + '.' + EvalMethodConvention.generatedClassName(path);
    }

    @Test
    public void testAllCorpusStaticUnitsBindAndMatchInterpreterColumn() {
        installFullSupplies();
        int javaPass = 0;
        for (CompareUnit unit : harnessUnits) {
            CompareUnitReport report = harness.runUnit(unit);
            assertTrue(report.isPassed(), "unit diverged: " + report);
            assertTrue(report.getColumnOutcomes().stream()
                    .anyMatch(o -> CompareBackendIds.JAVA.equals(o.getBackendId()) && o.isPassed()),
                    "java production-binding column must execute and pass: " + unit.getName());
            javaPass++;
        }
        assertEquals(harnessUnits.size(), javaPass, "all corpus static units executed via production binding");
    }

    @Test
    public void testLoadedModelIdentityIsGeneratedBinding() {
        installFullSupplies();
        // 身份断言（载体 = 加载后模型执行体/裁决环 artifact）：夹具类入口 Method + bound 包装
        for (CompareUnit unit : harnessUnits) {
            ProductionBindingCorpus.StaticUnit staticUnit = ProductionBindingCorpus.staticUnits().stream()
                    .filter(u -> u.getPath().equals(unit.getSourceLocationPath())).findFirst().orElseThrow();
            io.nop.xlang.api.XplModel model = ProductionBindingCorpus.loadBound(staticUnit);
            assertTrue(model.getExpr() instanceof EvalStaticBoundExecutable,
                    "loaded model must be bound: " + unit.getName());
            Object artifact = ((EvalStaticBoundExecutable) model.getExpr()).getBinding().getBindingArtifact();
            Method entry = assertInstanceOf(Method.class, artifact);
            assertEquals(fixtureFqn(unit.getSourceLocationPath()), entry.getDeclaringClass().getName());
        }
    }

    // ---- 验收第二项：指纹失配/清单缺失降级断言（红/绿对照） ----

    @Test
    public void testFingerprintMismatchInjectsInterpreterWithGradedObservation() {
        // 红：合成清单指纹与树不符（corpus 首单元）→ INTERPRETER 执行 + 分级观测
        ProductionBindingCorpus.StaticUnit unit = ProductionBindingCorpus.staticUnits().get(0);
        Map<String, GeneratedClassManifest.Entry> entries = new LinkedHashMap<>();
        entries.put(unit.getPath(), new GeneratedClassManifest.Entry(unit.getPath(),
                fixtureFqn(unit.getPath()),
                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"));
        JavaEvalExecutionBackend.instance().setGeneratedClassManifest(GeneratedClassManifest.of(entries));
        JavaEvalExecutionBackend.instance().setStaticScanList(Collections.singletonList(unit.getPath()));

        double before = EvalBackendObservation.degradationCount("java",
                EvalBackendObservation.REASON_GENERATED_FINGERPRINT_MISMATCH);
        io.nop.xlang.api.XplModel model = ProductionBindingCorpus.loadBound(unit);
        assertTrue(model.getExpr() instanceof EvalStaticDegradedExecutable, "mismatch must degrade");

        IEvalScope scope = EvalExprProvider.newEvalScope(new LinkedHashMap<>(unit.getUnit().getInputVars()));
        Object degradedResult = model.invoke(scope);
        Object baseline = EvalExprProvider.getGlobalExecutor().execute(
                ProductionBindingCorpus.compileCanonicalTree(unit),
                new EvalRuntime(EvalExprProvider.newEvalScope(
                        new LinkedHashMap<>(unit.getUnit().getInputVars()))));
        assertTrue(CompareValues.typedEquals(baseline, degradedResult),
                "degraded execution result must equal interpreter baseline");
        assertEquals(1.0, EvalBackendObservation.degradationCount("java",
                EvalBackendObservation.REASON_GENERATED_FINGERPRINT_MISMATCH) - before, 1e-9);

        EvalBackendDecision decision = EvalBackendRouter.instance().getRecentDecisions().get(0);
        assertEquals(EvalBackendDecision.RouteKind.INTERPRETER, decision.getKind());
        assertTrue(decision.isDegraded());
    }

    @Test
    public void testMissingGeneratedClassInjectsInterpreterWithObservation() {
        // 红：清单内类缺失（合法指纹指向不存在类名）→ INTERPRETER + generated-binding-missing
        ProductionBindingCorpus.StaticUnit unit = ProductionBindingCorpus.staticUnits().get(0);
        Map<String, GeneratedClassManifest.Entry> entries = new LinkedHashMap<>();
        entries.put(unit.getPath(), new GeneratedClassManifest.Entry(unit.getPath(),
                "io.nop.xlang.gen.Gen___class_not_on_classpath",
                ExecutableTreeFingerprints.fingerprint(ProductionBindingCorpus.compileCanonicalTree(unit))));
        JavaEvalExecutionBackend.instance().setGeneratedClassManifest(GeneratedClassManifest.of(entries));
        JavaEvalExecutionBackend.instance().setStaticScanList(Collections.singletonList(unit.getPath()));

        double before = EvalBackendObservation.degradationCount("java",
                EvalBackendObservation.REASON_GENERATED_BINDING_MISSING);
        io.nop.xlang.api.XplModel model = ProductionBindingCorpus.loadBound(unit);
        assertTrue(model.getExpr() instanceof EvalStaticDegradedExecutable);
        Object result = model.invoke(EvalExprProvider.newEvalScope(
                new LinkedHashMap<>(unit.getUnit().getInputVars())));
        assertNotNull(result);
        assertEquals(1.0, EvalBackendObservation.degradationCount("java",
                EvalBackendObservation.REASON_GENERATED_BINDING_MISSING) - before, 1e-9);
    }

    @Test
    public void testGreenLightHitBindingHasZeroDegradation() {
        // 绿灯对照：命中绑定 → 零降级观测（counter 不动）（自装供给）
        ProductionBindingCorpus.StaticUnit unit = ProductionBindingCorpus.staticUnits().get(0);
        Map<String, GeneratedClassManifest.Entry> entries = new LinkedHashMap<>();
        entries.put(unit.getPath(), new GeneratedClassManifest.Entry(unit.getPath(),
                fixtureFqn(unit.getPath()),
                ExecutableTreeFingerprints.fingerprint(ProductionBindingCorpus.compileCanonicalTree(unit))));
        JavaEvalExecutionBackend.instance().setGeneratedClassManifest(GeneratedClassManifest.of(entries));
        JavaEvalExecutionBackend.instance().setStaticScanList(Collections.singletonList(unit.getPath()));

        double missing = EvalBackendObservation.degradationCount("java",
                EvalBackendObservation.REASON_GENERATED_BINDING_MISSING);
        double mismatch = EvalBackendObservation.degradationCount("java",
                EvalBackendObservation.REASON_GENERATED_FINGERPRINT_MISMATCH);
        double steady = EvalBackendObservation.degradationCount("java",
                EvalBackendObservation.REASON_TENANT_DIVERGENT_TREE);

        io.nop.xlang.api.XplModel model = ProductionBindingCorpus.loadBound(unit);
        assertTrue(model.getExpr() instanceof EvalStaticBoundExecutable);
        model.invoke(EvalExprProvider.newEvalScope(
                new LinkedHashMap<>(unit.getUnit().getInputVars())));

        assertEquals(0.0, EvalBackendObservation.degradationCount("java",
                EvalBackendObservation.REASON_GENERATED_BINDING_MISSING) - missing, 1e-9);
        assertEquals(0.0, EvalBackendObservation.degradationCount("java",
                EvalBackendObservation.REASON_GENERATED_FINGERPRINT_MISMATCH) - mismatch, 1e-9);
        assertEquals(0.0, EvalBackendObservation.degradationCount("java",
                EvalBackendObservation.REASON_TENANT_DIVERGENT_TREE) - steady, 1e-9);
    }

    // ---- 验收第三项：清单外动态路径零降级断言 ----

    @Test
    public void testOutOfListResourceRoutesDynamicPathWithZeroDegradation() {
        // 清单外资源经真实出口（compileSimpleExpr / compileTag）→ 动态路径裁决 + 零降级观测增量
        double missing = EvalBackendObservation.degradationCount("java",
                EvalBackendObservation.REASON_GENERATED_BINDING_MISSING);
        double disabled = EvalBackendObservation.degradationCount("java",
                EvalBackendObservation.REASON_CONFIG_DISABLED);

        SourceLocation loc = SourceLocation.fromPath("/dynamic-out-of-list/not-scanned.expr");
        ExprEvalAction action = XLang.newCompileTool().allowUnregisteredScopeVar(true)
                .compileSimpleExpr(loc, "6 * 7");
        assertEquals(42, action.invoke(EvalExprProvider.newEvalScope()));

        EvalBackendDecision decision = EvalBackendRouter.instance().getRecentDecisions().get(0);
        assertEquals(EvalBackendDecision.RouteKind.INTERPRETER, decision.getKind(),
                "no truffle in this module: dynamic path adjudicates to interpreter silently");
        assertFalse(decision.isDegraded(), "dynamic-backend-not-registered is a silent path (no event)");

        assertEquals(0.0, EvalBackendObservation.degradationCount("java",
                EvalBackendObservation.REASON_GENERATED_BINDING_MISSING) - missing, 1e-9);
        assertEquals(0.0, EvalBackendObservation.degradationCount("java",
                EvalBackendObservation.REASON_CONFIG_DISABLED) - disabled, 1e-9);
    }

    // ---- 生产绑定路径 java 列（harness 列：模型加载 → 绑定 → invoke；非 JavaBackendColumn 直驱） ----

    static final class ProductionBindingColumn implements IEvalBackendColumn {

        static volatile io.nop.xlang.api.XplModel lastLoadedModel;

        @Override
        public String getBackendId() {
            return CompareBackendIds.JAVA;
        }

        @Override
        public boolean isSupportsStaticUnits() {
            return true;
        }

        @Override
        public boolean isSupportsDynamicUnits() {
            return false;
        }

        @Override
        public BackendExecutionResult execute(BackendExecRequest request) {
            ProductionBindingCorpus.StaticUnit unit = ProductionBindingCorpus.staticUnits().stream()
                    .filter(u -> u.getPath().equals(request.getUnit().getSourceLocationPath()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "production column requires a corpus unit: " + request.getUnit().getName()));

            // 生产绑定路径全链：模型加载（parseXpl + 绑定 hook）→ 已绑定执行体 → invoke（choke point）
            io.nop.xlang.api.XplModel model = ProductionBindingCorpus.loadBound(unit);
            lastLoadedModel = model;
            IExecutableExpression expr = model.getExpr();
            if (!(expr instanceof EvalStaticBoundExecutable))
                throw new IllegalStateException("production column requires a bound model (unit degradation "
                        + "would be a unit-level FAIL): " + unit.getPath());
            Method entry = (Method) ((EvalStaticBoundExecutable) expr).getBinding().getBindingArtifact();

            Object generatedInstance;
            try {
                generatedInstance = entry.getDeclaringClass().getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("fixture class instantiation failed", e);
            }
            BackendExecutionEvidence evidence = new BackendExecutionEvidence(generatedInstance, entry);
            try {
                // 生产执行通路：已绑定执行体经 choke point（XLang.execute）直通绑定体——
                // scope 与输出缓冲（harness 录制缓冲）经 EvalRuntime 传入，$out 双参单元同通路
                Object ret = XLang.execute(expr, new EvalRuntime(request.getScope(), request.getOut()));
                return BackendExecutionResult.value(ret, evidence);
            } catch (Throwable t) {
                return BackendExecutionResult.error(t, evidence);
            }
        }
    }
}
