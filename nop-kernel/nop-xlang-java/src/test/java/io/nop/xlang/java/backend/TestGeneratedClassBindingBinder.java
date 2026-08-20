package io.nop.xlang.java.backend;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.api.XLang;
import io.nop.xlang.backend.EvalBackendDecision;
import io.nop.xlang.backend.EvalBackendObservation;
import io.nop.xlang.backend.EvalBackendRouter;
import io.nop.xlang.backend.IEvalStaticBinding;
import io.nop.xlang.compare.CompareValues;
import io.nop.xlang.java.compare.ProductionBindingCorpus;
import io.nop.xlang.java.gen.EvalMethodConvention;
import io.nop.xlang.java.gen.ExecutableTreeFingerprints;
import io.nop.xlang.java.gen.GeneratedClassManifest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 生产 binder 单测（I10 Phase 2）：D2 内存契约消费 + classpath 常规加载 + D1 指纹校验 +
 * D5 分级降级观测（stale 每次WARN / 稳态 once-per-path 去重、指标不衰减）。
 * 生成类执行体 = D6 夹具物化（corpus 单元转译产物随 Maven test 编译，Class.forName 常规加载）。
 */
public class TestGeneratedClassBindingBinder {

    private static final ProductionBindingCorpus.StaticUnit UNIT = firstUnit("scope-chain");

    private static final String UNIT_PATH = UNIT.getPath();

    private static final String FIXTURE_FQN = EvalMethodConvention.GENERATED_PACKAGE + '.'
            + EvalMethodConvention.generatedClassName(UNIT_PATH);

    private static IExecutableExpression unitTree;

    private static String unitFingerprint;

    private ListAppender<ILoggingEvent> appender;

    private Logger observationLogger;

    private static ProductionBindingCorpus.StaticUnit firstUnit(String namePart) {
        return ProductionBindingCorpus.staticUnits().stream()
                .filter(u -> u.getPath().contains(namePart))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("corpus unit not found: " + namePart));
    }

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
        unitTree = ProductionBindingCorpus.compileCanonicalTree(UNIT);
        unitFingerprint = ExecutableTreeFingerprints.fingerprint(unitTree);
    }

    @AfterAll
    public static void resetBackend() {
        JavaEvalExecutionBackend.instance().setStaticScanList(java.util.Collections.emptySet());
        JavaEvalExecutionBackend.instance().setBinder(null);
        JavaEvalExecutionBackend.instance().clearUnavailable();
        EvalBackendRouter.instance().clearRecentDecisions();
    }

    @BeforeEach
    public void setUp() {
        EvalBackendObservation.clearSteadyStateDedup();
        observationLogger = (Logger) LoggerFactory.getLogger(EvalBackendObservation.class);
        appender = new ListAppender<>();
        appender.start();
        observationLogger.addAppender(appender);
        EvalBackendRouter.instance().clearRecentDecisions();
    }

    @AfterEach
    public void tearDown() {
        observationLogger.detachAppender(appender);
        appender.stop();
        JavaEvalExecutionBackend.instance().setStaticScanList(java.util.Collections.emptySet());
        JavaEvalExecutionBackend.instance().setBinder(null);
        EvalBackendRouter.instance().clearRecentDecisions();
    }

    private static GeneratedClassManifest manifestOf(GeneratedClassManifest.Entry entry) {
        return GeneratedClassManifest.of(new java.util.LinkedHashMap<>(Map.of(UNIT_PATH, entry)));
    }

    // ---- 正：清单命中 → classpath 常规加载 → 执行（三层之值层 vs 解释器） ----

    @Test
    public void testManifestHitLoadsAndExecutesFixtureClass() {
        GeneratedClassBindingBinder binder = new GeneratedClassBindingBinder(
                manifestOf(new GeneratedClassManifest.Entry(UNIT_PATH, FIXTURE_FQN, unitFingerprint)));
        IEvalStaticBinding binding = binder.findStaticBinding(UNIT_PATH, unitTree);
        assertNotNull(binding);

        Method entry = (Method) binding.getBindingArtifact();
        assertNotNull(entry);
        assertEquals(FIXTURE_FQN, entry.getDeclaringClass().getName());

        IEvalScope routed = EvalExprProvider.newEvalScope(Map.of("x", 5));
        Object routedResult = binding.execute(new EvalRuntime(routed));
        IEvalScope baseline = EvalExprProvider.newEvalScope(Map.of("x", 5));
        Object baselineResult = EvalExprProvider.getGlobalExecutor()
                .execute(unitTree, new EvalRuntime(baseline));
        assertTrue(CompareValues.typedEquals(baselineResult, routedResult));
        assertEquals(UNIT.getUnit().getExpectation().getReturnValue(), routedResult,
                "generated-class execution matches corpus declared expectation");
    }

    // ---- 负：清单条目缺失 → generated-binding-missing（stale 每次WARN + 计数） ----

    @Test
    public void testEntryMissingDegradesWithObservation() {
        double before = EvalBackendObservation.degradationCount("java",
                EvalBackendObservation.REASON_GENERATED_BINDING_MISSING);
        GeneratedClassBindingBinder binder = new GeneratedClassBindingBinder(GeneratedClassManifest.empty());
        assertNull(binder.findStaticBinding(UNIT_PATH, unitTree));
        assertEquals(1.0, EvalBackendObservation.degradationCount("java",
                EvalBackendObservation.REASON_GENERATED_BINDING_MISSING) - before, 1e-9);
        assertEquals(1, countWarns(EvalBackendObservation.REASON_GENERATED_BINDING_MISSING));
    }

    // ---- 负：指纹失配无租户 → stale（generated-fingerprint-mismatch） ----

    @Test
    public void testFingerprintMismatchWithoutTenantIsStaleDegradation() {
        double before = EvalBackendObservation.degradationCount("java",
                EvalBackendObservation.REASON_GENERATED_FINGERPRINT_MISMATCH);
        GeneratedClassManifest.Entry wrong = new GeneratedClassManifest.Entry(UNIT_PATH, FIXTURE_FQN,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        GeneratedClassBindingBinder binder = new GeneratedClassBindingBinder(manifestOf(wrong));
        assertNull(binder.findStaticBinding(UNIT_PATH, unitTree));
        assertEquals(1.0, EvalBackendObservation.degradationCount("java",
                EvalBackendObservation.REASON_GENERATED_FINGERPRINT_MISMATCH) - before, 1e-9);
        assertEquals(1, countWarns(EvalBackendObservation.REASON_GENERATED_FINGERPRINT_MISMATCH));
    }

    // ---- 负：指纹失配 + 租户上下文 → 稳态（tenant-divergent-tree，WARN 去重 + 指标不衰减） ----

    @Test
    public void testFingerprintMismatchWithTenantIsSteadyStateWithDedup() {
        double before = EvalBackendObservation.degradationCount("java",
                EvalBackendObservation.REASON_TENANT_DIVERGENT_TREE);
        GeneratedClassManifest.Entry wrong = new GeneratedClassManifest.Entry(UNIT_PATH, FIXTURE_FQN,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        GeneratedClassBindingBinder binder = new GeneratedClassBindingBinder(manifestOf(wrong));

        // 两次绑定（同一 sourceKey）：WARN 一次、计数两次
        ContextProvider.runWithTenant("tenant-b", () -> {
            assertNull(binder.findStaticBinding(UNIT_PATH, unitTree));
            assertNull(binder.findStaticBinding(UNIT_PATH, unitTree));
            return null;
        });

        assertEquals(2.0, EvalBackendObservation.degradationCount("java",
                EvalBackendObservation.REASON_TENANT_DIVERGENT_TREE) - before, 1e-9,
                "metric count does not decay");
        assertEquals(1, countWarns(EvalBackendObservation.REASON_TENANT_DIVERGENT_TREE),
                "steady-state WARN deduped once per path");
    }

    // ---- 负：清单内类缺失（classpath 无该类）→ generated-binding-missing ----

    @Test
    public void testMissingGeneratedClassDegradesWithObservation() {
        double before = EvalBackendObservation.degradationCount("java",
                EvalBackendObservation.REASON_GENERATED_BINDING_MISSING);
        GeneratedClassManifest.Entry missing = new GeneratedClassManifest.Entry(UNIT_PATH,
                "io.nop.xlang.gen.Gen___not_on_classpath___", unitFingerprint);
        GeneratedClassBindingBinder binder = new GeneratedClassBindingBinder(manifestOf(missing));
        assertNull(binder.findStaticBinding(UNIT_PATH, unitTree));
        assertEquals(1.0, EvalBackendObservation.degradationCount("java",
                EvalBackendObservation.REASON_GENERATED_BINDING_MISSING) - before, 1e-9);
    }

    // ---- 非法清单数据注入时 fail-fast（D2 裁定） ----

    @Test
    public void testMalformedManifestFailsFastAtInjection() {
        Map<String, GeneratedClassManifest.Entry> entries = new java.util.LinkedHashMap<>();

        entries.put("p", new GeneratedClassManifest.Entry("p", "Not A Class Name", unitFingerprint));
        assertThrows(NopException.class, () -> GeneratedClassManifest.of(entries));

        entries.put("p", new GeneratedClassManifest.Entry("p", FIXTURE_FQN, "not-hex"));
        assertThrows(NopException.class, () -> GeneratedClassManifest.of(entries));

        entries.put("p", new GeneratedClassManifest.Entry("other", FIXTURE_FQN, unitFingerprint));
        assertThrows(NopException.class, () -> GeneratedClassManifest.of(entries));

        // 合法条目通过
        entries.put("p", new GeneratedClassManifest.Entry("p", FIXTURE_FQN, unitFingerprint));
        assertEquals(1, GeneratedClassManifest.of(entries).size());
    }

    // ---- 清单外资源：裁决入口不咨询 binder（计数断言） ----

    @Test
    public void testOutOfListResourceDoesNotConsultBinder() {
        AtomicInteger consults = new AtomicInteger();
        JavaEvalExecutionBackend.instance().setBinder((resourcePath, tree) -> {
            consults.incrementAndGet();
            return null;
        });
        JavaEvalExecutionBackend.instance().setStaticScanList(List.of(UNIT_PATH));

        SourceLocation loc = SourceLocation.fromPath("/dynamic/not-in-scan-list.expr");
        Object result = XLang.newCompileTool().allowUnregisteredScopeVar(true)
                .compileSimpleExpr(loc, "1 + 2").invoke(EvalExprProvider.newEvalScope());
        assertEquals(3, result);
        assertEquals(0, consults.get(), "out-of-list resource must not consult the binder");

        EvalBackendDecision decision = EvalBackendRouter.instance().getRecentDecisions().get(0);
        assertEquals(EvalBackendDecision.RouteKind.INTERPRETER, decision.getKind());
        assertTrue(!decision.isDegraded(), "dynamic-backend-not-registered is a silent interpreter path");
    }

    private long countWarns(String reason) {
        return appender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .filter(e -> e.getFormattedMessage().contains("reason=" + reason))
                .count();
    }
}
