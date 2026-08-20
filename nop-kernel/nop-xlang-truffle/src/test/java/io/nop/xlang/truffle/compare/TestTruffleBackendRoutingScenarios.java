package io.nop.xlang.truffle.compare;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.xlang.api.ExprEvalAction;
import io.nop.xlang.api.XLang;
import io.nop.xlang.backend.EvalBackendDecision;
import io.nop.xlang.backend.EvalBackendObservation;
import io.nop.xlang.backend.EvalBackendRegistry;
import io.nop.xlang.backend.EvalBackendRouter;
import io.nop.xlang.compare.CompareUnit;
import io.nop.xlang.compare.CompareUnitKind;
import io.nop.xlang.compare.CompareUnitReport;
import io.nop.xlang.compare.CompareValues;
import io.nop.xlang.compare.ColumnOutcome;
import io.nop.xlang.compare.ColumnSkipRecord;
import io.nop.xlang.compare.ExpectedOutcome;
import io.nop.xlang.compare.ExecCompareHarness;
import io.nop.xlang.exec.AbstractExecutable;
import io.nop.xlang.exec.LiteralExecutable;
import io.nop.xlang.exec.PlusExecutable;
import io.nop.xlang.exec.SeqExecutable;
import io.nop.xlang.truffle.backend.TruffleEvalExecutionBackend;
import io.nop.xlang.truffle.nodes.XLangRootNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static io.nop.xlang.XLangConfigs.CFG_XLANG_EXECUTION_FORCE_INTERPRETER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 路由场景矩阵 truffle 侧（Phase 1 §6 落点 c；roadmap I9 验收）：场景用例经真实出口
 * （compile→invoke→统一裁决入口）执行，以 I1 harness 断言工具（CompareValues）+ 裁决环身份
 * 断言（truffle=翻译 AST XLangRootNode）+ 降级观测断言（WARN ListAppender + 指标）收口；
 * 单元级降级判 FAIL 场景另经 {@link ExecCompareHarness} + truffle 列直驱（对拍不变式：
 * 单元级降级判 FAIL 不判跳过）。
 */
public class TestTruffleBackendRoutingScenarios {

    private static IConfigReference<Boolean> forceSwitch;

    private static Boolean forceBaseline;

    private ListAppender<ILoggingEvent> appender;

    private Logger observationLogger;

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
        forceSwitch = CFG_XLANG_EXECUTION_FORCE_INTERPRETER;
        forceBaseline = forceSwitch.get();
    }

    @AfterAll
    public static void resetAll() {
        AppConfig.getConfigProvider().updateConfigValue(forceSwitch, forceBaseline);
        EvalBackendRouter.instance().clearRecentDecisions();
    }

    @BeforeEach
    public void setUp() {
        observationLogger = (Logger) LoggerFactory.getLogger(EvalBackendObservation.class);
        appender = new ListAppender<>();
        appender.start();
        observationLogger.addAppender(appender);
        EvalBackendRouter.instance().clearRecentDecisions();
        AppConfig.getConfigProvider().updateConfigValue(forceSwitch, false);
    }

    @AfterEach
    public void tearDown() {
        observationLogger.detachAppender(appender);
        appender.stop();
        AppConfig.getConfigProvider().updateConfigValue(forceSwitch, false);
        EvalBackendRouter.instance().clearRecentDecisions();
    }

    private static void restoreSingletonBackend(Runnable body) {
        TruffleEvalExecutionBackend singleton = TruffleEvalExecutionBackend.instance();
        EvalBackendRegistry registry = EvalBackendRegistry.instance();
        registry.unregister(singleton);
        try {
            body.run();
        } finally {
            if (registry.getBackend(singleton.getBackendId()) != singleton) {
                registry.unregister(registry.getBackend(singleton.getBackendId()));
                registry.register(singleton);
            }
        }
    }

    // ---- 场景一：动态→truffle（经池运行时） ----

    @Test
    public void testDynamicRoutesToTrufflePool() {
        SourceLocation loc = SourceLocation.fromPath("t:/route/dynamic/truffle-scenario.expr");
        ExprEvalAction action = XLang.newCompileTool().allowUnregisteredScopeVar(true)
                .compileSimpleExpr(loc, "x * 2 + 2");
        IExecutableExpression tree = action.getExpr();

        IEvalScope scope = EvalExprProvider.newEvalScope(Map.of("x", 20));
        Object routedResult = action.invoke(scope);
        assertEquals(42, routedResult);

        // 身份断言：裁决落点 DYNAMIC，执行体 = 翻译 AST（sourceTree 即请求树实例——经池运行时驱动）
        EvalBackendDecision decision = EvalBackendRouter.instance().getRecentDecisions().get(0);
        assertEquals(EvalBackendDecision.RouteKind.DYNAMIC, decision.getKind());
        assertEquals("truffle", decision.getBackendId());
        assertFalse(decision.isDegraded());
        XLangRootNode rootNode = assertInstanceOf(XLangRootNode.class, decision.getArtifact());
        assertSame(tree, rootNode.getSourceTree());

        // 三层对拍：同一出口强制解释器（诊断模式）→ 解释器执行，返回值 + scope 一致
        AppConfig.getConfigProvider().updateConfigValue(forceSwitch, true);
        IEvalScope baselineScope = EvalExprProvider.newEvalScope(Map.of("x", 20));
        Object baselineResult = action.invoke(baselineScope);
        assertTrue(CompareValues.typedEquals(baselineResult, routedResult),
                "baseline=" + baselineResult + " routed=" + routedResult);
        assertEquals(20, baselineScope.getValue("x"));
        assertEquals(20, scope.getValue("x"));
        // 强制解释器为静默诊断模式：不产生降级观测
        assertTrue(appender.list.stream().noneMatch(e -> e.getLevel() == Level.WARN));
    }

    // ---- 场景二：truffle 降级→解释器（初始化失败注入） ----

    @Test
    public void testTruffleDegradedByInitFailure() {
        restoreSingletonBackend(() -> {
            TruffleEvalExecutionBackend failing = new TruffleEvalExecutionBackend(() -> {
                throw new IllegalStateException("injected engine failure");
            });
            failing.probeInitialization();
            EvalBackendRegistry.instance().register(failing);

            SourceLocation loc = SourceLocation.fromPath("t:/route/dynamic/degrade.expr");
            ExprEvalAction action = XLang.newCompileTool().allowUnregisteredScopeVar(true)
                    .compileSimpleExpr(loc, "11 * 11");
            IExecutableExpression tree = action.getExpr();

            double before = EvalBackendObservation.degradationCount("truffle",
                    EvalBackendObservation.REASON_UNAVAILABLE);
            Object result = action.invoke(EvalExprProvider.newEvalScope());
            assertEquals(121, result);

            // 身份断言：解释器执行（裁决环 artifact = 原树实例）+ 降级观测
            EvalBackendDecision decision = EvalBackendRouter.instance().getRecentDecisions().get(0);
            assertEquals(EvalBackendDecision.RouteKind.INTERPRETER, decision.getKind());
            assertTrue(decision.isDegraded());
            assertTrue(decision.getReason().contains("injected engine failure"));
            assertSame(tree, decision.getArtifact());

            assertEquals(1.0, EvalBackendObservation.degradationCount("truffle",
                    EvalBackendObservation.REASON_UNAVAILABLE) - before, 1e-9);
            assertTrue(appender.list.stream().anyMatch(e -> e.getLevel() == Level.WARN
                            && e.getFormattedMessage().contains("reason=" + EvalBackendObservation.REASON_UNAVAILABLE)),
                    () -> String.valueOf(appender.list));
        });
    }

    // ---- 场景三：单元级降级（第三分支）——生产路由路径 ----

    /**
     * 支持集外合成节点（翻译失败注入载体，覆盖矩阵红灯注入同款做法）。
     */
    static final class UnsupportedNode extends AbstractExecutable {
        UnsupportedNode(SourceLocation loc) {
            super(loc);
        }

        @Override
        public void display(StringBuilder sb) {
            sb.append("unsupported");
        }

        @Override
        public Object execute(IExpressionExecutor executor, EvalRuntime rt) {
            return null;
        }
    }

    @Test
    public void testUnitLevelTranslationFailureFallsBackToInterpreter() {
        SourceLocation loc = SourceLocation.fromPath("t:/route/dynamic/unit-degrade.expr");
        IExecutableExpression tree = SeqExecutable.valueOf(loc, new IExecutableExpression[]{
                new UnsupportedNode(loc), LiteralExecutable.build(loc, "ok")});
        ExprEvalAction action = new ExprEvalAction(tree);

        double before = EvalBackendObservation.degradationCount("truffle",
                EvalBackendObservation.REASON_UNIT_TRANSLATION_FAILURE);
        double unavailableBefore = EvalBackendObservation.degradationCount("truffle",
                EvalBackendObservation.REASON_UNAVAILABLE);

        Object result = action.invoke(EvalExprProvider.newEvalScope());
        assertEquals("ok", result);

        // 第三分支：翻译失败事件被消费（fallback 仅在事件关联命中时发生）→ 降级解释器 + 观测
        EvalBackendDecision decision = EvalBackendRouter.instance().getRecentDecisions().get(0);
        assertEquals(EvalBackendDecision.RouteKind.INTERPRETER, decision.getKind());
        assertTrue(decision.isDegraded());
        assertEquals(EvalBackendObservation.REASON_UNIT_TRANSLATION_FAILURE, decision.getReason());
        assertSame(tree, decision.getArtifact());

        assertEquals(1.0, EvalBackendObservation.degradationCount("truffle",
                EvalBackendObservation.REASON_UNIT_TRANSLATION_FAILURE) - before, 1e-9);
        assertEquals(0.0, EvalBackendObservation.degradationCount("truffle",
                EvalBackendObservation.REASON_UNAVAILABLE) - unavailableBefore, 1e-9);
        assertTrue(appender.list.stream().anyMatch(e -> e.getLevel() == Level.WARN
                        && e.getFormattedMessage()
                        .contains("reason=" + EvalBackendObservation.REASON_UNIT_TRANSLATION_FAILURE)),
                () -> String.valueOf(appender.list));

        // 红/绿对照（绿）：可翻译树 → DYNAMIC 裁决、无单元级降级观测
        EvalBackendRouter.instance().clearRecentDecisions();
        double greenBefore = EvalBackendObservation.degradationCount("truffle",
                EvalBackendObservation.REASON_UNIT_TRANSLATION_FAILURE);
        IExecutableExpression supported = new PlusExecutable(loc,
                LiteralExecutable.build(loc, 20), LiteralExecutable.build(loc, 22));
        Object green = new ExprEvalAction(supported).invoke(EvalExprProvider.newEvalScope());
        assertEquals(42, green);
        assertEquals(EvalBackendDecision.RouteKind.DYNAMIC,
                EvalBackendRouter.instance().getRecentDecisions().get(0).getKind());
        assertEquals(0.0, EvalBackendObservation.degradationCount("truffle",
                EvalBackendObservation.REASON_UNIT_TRANSLATION_FAILURE) - greenBefore, 1e-9);
    }

    // ---- 场景四：单元级降级判 FAIL 不判 SKIP（对拍不变式，经 I1 框架执行） ----

    @Test
    public void testUnitLevelDegradationJudgesFailNotSkipInHarness() {
        try (TruffleBackendColumn column = TruffleBackendColumn.open()) {
            // 红灯：支持集外节点 → truffle 列翻译失败 → 列 FAIL（非 SKIP），解释器列 PASS
            SourceLocation loc = SourceLocation.fromPath("xlang-compare/dynamic/routing-unsupported.expr");
            IExecutableExpression unsupportedTree = SeqExecutable.valueOf(loc, new IExecutableExpression[]{
                    new UnsupportedNode(loc), LiteralExecutable.build(loc, "ok")});

            CompareUnit unit = new CompareUnit("routing-unsupported", CompareUnitKind.DYNAMIC,
                    "unsupported", "xlang-compare/dynamic/routing-unsupported.expr",
                    Map.of(), ExpectedOutcome.returnValue("ok"), "routing");

            ExecCompareHarness harness = ExecCompareHarness.withInterpreterBaseline();
            harness.setCompiler(u -> unsupportedTree);
            harness.registerColumn(column);
            harness.registerIdentityRule(new TruffleBackendIdentityRule());

            CompareUnitReport report = harness.runUnit(unit);
            assertFalse(report.isPassed(), () -> report.toString());

            ColumnOutcome interpreterOutcome = report.getColumnOutcomes().stream()
                    .filter(o -> "interpreter".equals(o.getBackendId())).findFirst().orElse(null);
            ColumnOutcome truffleOutcome = report.getColumnOutcomes().stream()
                    .filter(o -> "truffle".equals(o.getBackendId())).findFirst().orElse(null);
            assertNotNull(interpreterOutcome);
            assertNotNull(truffleOutcome);
            assertTrue(interpreterOutcome.isPassed(), () -> String.join(";", interpreterOutcome.getFailures()));
            assertEquals(ColumnOutcome.Status.FAIL, truffleOutcome.getStatus(), () -> report.toString());
            assertTrue(truffleOutcome.getFailures().stream().anyMatch(f -> f.contains("column-crashed")),
                    () -> String.join(";", truffleOutcome.getFailures()));
            // 判 FAIL 非 SKIP：truffle 列不在缺席记录中
            assertTrue(report.getSkipRecords().stream().noneMatch(s -> "truffle".equals(s.getBackendId())),
                    () -> String.valueOf(report.getSkipRecords()));

            // 绿灯对照：可翻译单元 → 两列全 PASS（FAIL 语义非恒真）
            IExecutableExpression supportedTree = new PlusExecutable(loc,
                    LiteralExecutable.build(loc, 20), LiteralExecutable.build(loc, 22));
            CompareUnit greenUnit = new CompareUnit("routing-supported", CompareUnitKind.DYNAMIC,
                    "20 + 22", "xlang-compare/dynamic/routing-supported.expr",
                    Map.of(), ExpectedOutcome.returnValue(42), "routing");
            ExecCompareHarness greenHarness = ExecCompareHarness.withInterpreterBaseline();
            greenHarness.setCompiler(u -> supportedTree);
            greenHarness.registerColumn(column);
            greenHarness.registerIdentityRule(new TruffleBackendIdentityRule());
            CompareUnitReport greenReport = greenHarness.runUnit(greenUnit);
            assertTrue(greenReport.isPassed(), () -> greenReport.toString());
        }
    }
}
