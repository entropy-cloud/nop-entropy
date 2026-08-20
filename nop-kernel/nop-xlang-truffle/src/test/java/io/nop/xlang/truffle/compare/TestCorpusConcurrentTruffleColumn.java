package io.nop.xlang.truffle.compare;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.EvalScopeImpl;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.initialize.CoreInitialization;
import io.nop.xlang.compare.ColumnOutcome;
import io.nop.xlang.compare.CompareBackendIds;
import io.nop.xlang.compare.CompareUnit;
import io.nop.xlang.compare.CompareUnitKind;
import io.nop.xlang.compare.CompareUnitReport;
import io.nop.xlang.compare.CompareValues;
import io.nop.xlang.compare.CorpusCoverageA;
import io.nop.xlang.compare.CorpusCoverageB;
import io.nop.xlang.compare.CorpusV1;
import io.nop.xlang.compare.ExecCompareHarness;
import io.nop.xlang.compare.ICompareUnitCompiler;
import io.nop.xlang.compare.RecordingEvalOutput;
import io.nop.xlang.compare.SideEffectSnapshot;
import io.nop.xlang.truffle.eval.XLangTruffleEval;
import io.nop.xlang.truffle.lang.XLangLanguage;
import io.nop.xlang.truffle.nodes.XLangRootNode;
import io.nop.xlang.truffle.runtime.XLangContextPool;
import io.nop.xlang.truffle.translate.TranslatedUnit;
import io.nop.xlang.truffle.translate.TreeFingerprints;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * SHARED 形态并发正确性对拍（roadmap I8 验收第一项；plan I8 Phase 1 §7 driver 承载裁定：
 * 独立并发测试类——harness 列契约为单列串行，并发对拍 = N 线程 × 池 × 每线程独立现场）。
 *
 * <p><b>对拍口径</b>（消费既有 corpus，不扩）：多线程经池并发求值（同一编译单元 N 线程 ×
 * 同单元 / 不同编译单元并发 = 线程 × corpus 单元分配）vs <b>解释器单线程基线</b>：
 * 三层断言逐线程比对（返回值 equals 含类型 / scope 变量与输出缓冲逐项 / 异常语义
 * 错误码 + SourceLocation 回映射）+ truffle 身份断言（翻译 AST 经 CallTarget 执行，
 * root sourceTree = 同一编译树实例）+ 共享翻译缓存实例同一性（跨线程同键 → 同一
 * {@code TranslatedUnit}，SHARED + 共享 Engine 真实激活的行为证据）+ <b>无跨 Context
 * 串值断言</b>（各线程输出缓冲调用序列 = 各自基线、scope 变量集 = 各自基线——任何跨
 * Context 串值都会表现为逐线程比对分歧）。
 */
public class TestCorpusConcurrentTruffleColumn {

    private static final int THREADS = 4;

    private static XLangContextPool pool;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
        pool = XLangContextPool.open(THREADS);
    }

    @AfterAll
    public static void destroy() {
        pool.close();
        CoreInitialization.destroy();
    }

    /**
     * corpus 案例装载（单元 + 其编译器配对）：v1 + 覆盖 A + 覆盖 B 的静态与动态单元全量。
     */
    private static final class CorpusCase {
        final CompareUnit unit;
        final ICompareUnitCompiler compiler;

        CorpusCase(CompareUnit unit, ICompareUnitCompiler compiler) {
            this.unit = unit;
            this.compiler = compiler;
        }

        @Override
        public String toString() {
            return unit.getName();
        }
    }

    static Stream<CorpusCase> cases() {
        return Stream.concat(
                Stream.concat(
                        CorpusV1.units().stream().map(u -> new CorpusCase(u, CorpusV1.Compiler.INSTANCE)),
                        CorpusCoverageA.units().stream()
                                .map(u -> new CorpusCase(u, CorpusCoverageA.Compiler.INSTANCE))),
                CorpusCoverageB.units().stream().map(u -> new CorpusCase(u, CorpusCoverageB.Compiler.INSTANCE)));
    }

    static List<CorpusCase> caseList() {
        return cases().collect(Collectors.toList());
    }

    /**
     * 同一编译单元并发（roadmap 验收第一项形态一）：THREADS 线程 × 同单元（每线程独立
     * scope/输出缓冲，经池租借各自 Context），逐线程三层对拍 vs 解释器单线程基线。
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    public void testConcurrentSameUnitMatchesInterpreterBaseline(CorpusCase corpusCase) throws Exception {
        CompareUnit unit = corpusCase.unit;
        IExecutableExpression tree = corpusCase.compiler.compile(unit);
        ColumnOutcome interpreter = interpreterBaseline(unit, tree);

        AtomicReference<TranslatedUnit> sharedUnit = new AtomicReference<>();
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                final int threadIndex = t;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await(30, TimeUnit.SECONDS);
                        runOnePoolEvaluation(unit, tree, interpreter, threadIndex, sharedUnit, failures);
                    } catch (Throwable e) {
                        failures.add(e);
                    }
                }));
            }
            assertTrue(ready.await(30, TimeUnit.SECONDS), "workers must reach the barrier");
            start.countDown();
            for (Future<?> future : futures)
                future.get(120, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        if (!failures.isEmpty())
            fail("concurrent same-unit evaluation diverged: " + failures);
        assertEquals(0, pool.leasedCount(), "pool leases must all be returned");

        // 共享翻译缓存实例同一性：跨线程同键 → 同一翻译产物（SHARED + 共享 Engine 行为证据）
        TranslatedUnit unitRef = sharedUnit.get();
        assertNotNull(unitRef, "translated unit must be resolved");
        IExecutableExpression executedSource = unitRef.getRootNode().getSourceTree();
        assertTrue(executedSource == tree
                        || TreeFingerprints.fingerprint(executedSource) == TreeFingerprints.fingerprint(tree),
                "translated root must derive from this unit's compiled tree (instance or shared-cache hit)");
        XLangLanguage language = unitRef.getRootNode().getXLangLanguage();
        assertTrue(language.isMultipleContextsInitialized(),
                "SHARED sharing must be activated for pooled concurrent evaluation");
    }

    /**
     * 不同编译单元并发（roadmap 验收第一项形态二）：THREADS 线程 × corpus 单元分片，
     * 同时起步各自顺序求值分片单元，逐单元三层对拍 vs 解释器基线——不同期望值的并发
     * 线程间任何串值都会直接表现为该线程比对分歧。
     */
    @Test
    public void testConcurrentDistinctUnitsMatchInterpreterBaseline() throws Exception {
        List<CorpusCase> all = caseList();
        int sliceSize = (all.size() + THREADS - 1) / THREADS;
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                final int threadIndex = t;
                final List<CorpusCase> slice = all.subList(t * sliceSize, Math.min((t + 1) * sliceSize, all.size()));
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await(30, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        failures.add(e);
                        return;
                    }
                    for (CorpusCase corpusCase : slice) {
                        try {
                            CompareUnit unit = corpusCase.unit;
                            IExecutableExpression tree = corpusCase.compiler.compile(unit);
                            ColumnOutcome interpreter = interpreterBaseline(unit, tree);
                            runOnePoolEvaluation(unit, tree, interpreter, threadIndex, new AtomicReference<>(),
                                    failures);
                        } catch (Throwable e) {
                            failures.add(new AssertionError("unit=" + corpusCase.unit.getName() + ": " + e, e));
                        }
                    }
                }));
            }
            assertTrue(ready.await(30, TimeUnit.SECONDS), "workers must reach the barrier");
            start.countDown();
            for (Future<?> future : futures)
                future.get(300, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        if (!failures.isEmpty())
            fail("concurrent distinct-unit evaluation diverged: " + failures);
        assertEquals(0, pool.leasedCount(), "pool leases must all be returned");
        assertEquals(THREADS, pool.availableCount(), "pool must be fully idle after concurrent runs");
    }

    /**
     * 池驱动的单次求值 + 逐线程三层对拍（对拍口径与 harness 共享断言工具同源）。
     */
    private static void runOnePoolEvaluation(CompareUnit unit, IExecutableExpression tree,
                                             ColumnOutcome interpreter, int threadIndex,
                                             AtomicReference<TranslatedUnit> sharedUnit,
                                             ConcurrentLinkedQueue<Throwable> failures) throws Exception {
        String sourceKey = unit.getKind() == CompareUnitKind.STATIC
                ? unit.getSourceLocationPath()
                : XLangTruffleEval.dynamicSourceKey(unit.getSource());
        IEvalScope scope = new EvalScopeImpl(new LinkedHashMap<>(unit.getInputVars()));
        RecordingEvalOutput out = new RecordingEvalOutput();

        XLangTruffleEval.TranslatedEval result;
        try (XLangContextPool.Lease lease = pool.lease()) {
            result = lease.eval(sourceKey, tree, scope, out);
        }

        String where = "unit=" + unit.getName() + ", thread=" + threadIndex;
        try {
            // 三层断言第一层：返回值 equals 含类型（异常单元 = 错误码 + SourceLocation 回映射）
            assertSameResult(where, interpreter, result);
            // 三层断言第二层：scope 变量与输出缓冲逐项（无跨 Context 串值断言的载体）
            SideEffectSnapshot baseline = interpreter.getSnapshot();
            assertScopeVars(where, baseline.getLocalVars(), scope);
            assertOutputCalls(where, baseline.getOutputCalls(), out.getCalls());
            // truffle 身份断言：翻译 AST 经 CallTarget 执行——执行产物的源树与本单元树
            // 同一实例（全新翻译）或结构等价（JVM 级共享翻译缓存命中：键 = sourceKey +
            // 树指纹，跨测试复用是 SHARED 缓存的正确语义而非解释器兜底）
            TranslatedUnit truffleUnit = result.getUnit();
            assertNotNull(truffleUnit, "translated unit must be resolved: " + where);
            IExecutableExpression executedSource = truffleUnit.getRootNode().getSourceTree();
            assertTrue(executedSource == tree
                            || TreeFingerprints.fingerprint(executedSource) == TreeFingerprints.fingerprint(tree),
                    "translated root must derive from this unit's compiled tree: " + where);
            TranslatedUnit seen = sharedUnit.get();
            if (seen != null)
                assertSame(seen, truffleUnit, "same key across threads must hit the shared translation cache: "
                        + where);
            else
                sharedUnit.compareAndSet(null, truffleUnit);
        } catch (AssertionError e) {
            failures.add(e);
        }
    }

    /**
     * 解释器单线程基线（同一棵树实例经 harness 解释器列执行；期望列 = 预期声明 + 身份规则
     * 驱动，作为并发线程结果的参照）。
     */
    private static ColumnOutcome interpreterBaseline(CompareUnit unit, IExecutableExpression tree) {
        ExecCompareHarness harness = ExecCompareHarness.withInterpreterBaseline();
        harness.setCompiler(unused -> tree);
        CompareUnitReport report = harness.runUnit(unit, tree);
        assertTrue(report.isPassed(), "interpreter baseline must pass the declared expectation: " + report);
        ColumnOutcome outcome = report.getColumnOutcomes().get(0);
        assertEquals(CompareBackendIds.INTERPRETER, outcome.getBackendId());
        return outcome;
    }

    private static void assertSameResult(String where, ColumnOutcome interpreter,
                                         XLangTruffleEval.TranslatedEval truffle) {
        Object expectedValue = interpreter.getExecution().getReturnValue();
        Throwable expectedThrown = interpreter.getExecution().getThrown();
        if (expectedThrown != null) {
            Throwable actual = truffle.getThrown();
            assertNotNull(actual, "exception expected but normal return: " + where);
            String expectedCode = expectedThrown instanceof NopException
                    ? ((NopException) expectedThrown).getErrorCode() : expectedThrown.getClass().getName();
            String actualCode = actual instanceof NopException
                    ? ((NopException) actual).getErrorCode() : actual.getClass().getName();
            assertEquals(expectedCode, actualCode, "exception error code must match interpreter baseline: " + where);
            SourceLocation expectedLoc = expectedThrown instanceof NopException
                    ? ((NopException) expectedThrown).getErrorLocation() : null;
            SourceLocation actualLoc = actual instanceof NopException
                    ? ((NopException) actual).getErrorLocation() : null;
            if (expectedLoc != null && actualLoc != null) {
                assertEquals(expectedLoc.getPath(), actualLoc.getPath(),
                        "exception location path must map back: " + where);
                if (expectedLoc.getLine() > 0)
                    assertEquals(expectedLoc.getLine(), actualLoc.getLine(),
                            "exception location line must map back: " + where);
            }
            return;
        }
        assertNull(truffle.getThrown(), "normal return expected but exception thrown: " + where
                + ": " + truffle.getThrown());
        assertTrue(CompareValues.typedEquals(expectedValue, truffle.getReturnValue()),
                "return value must equal interpreter baseline with type: " + where + ": "
                        + CompareValues.describeTypedMismatch(expectedValue, truffle.getReturnValue()));
    }

    private static void assertScopeVars(String where, Map<String, Object> baseline, IEvalScope scope) {
        Map<String, Object> actual = new LinkedHashMap<>();
        for (String key : scope.keySet())
            actual.put(key, scope.getLocalValue(key));
        if (!baseline.keySet().equals(actual.keySet()))
            throw new AssertionError("scope vars keyset diverged (cross-context bleed?): " + where
                    + ": expected=" + baseline.keySet() + ", was=" + actual.keySet());
        for (String key : baseline.keySet()) {
            if (!CompareValues.typedEquals(baseline.get(key), actual.get(key)))
                throw new AssertionError("scope var diverged[" + key + "] (cross-context bleed?): " + where
                        + ": " + CompareValues.describeTypedMismatch(baseline.get(key), actual.get(key)));
        }
    }

    private static void assertOutputCalls(String where, List<?> baseline, List<?> actual) {
        if (!Objects.equals(baseline, actual))
            throw new AssertionError("output calls diverged (cross-context bleed?): " + where
                    + ": expected=" + baseline + ", was=" + actual);
    }
}
